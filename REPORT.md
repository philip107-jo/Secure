# 시큐어 코딩 적용 보고서

- 이름: 조빌립
- 제출일: 2026-11-06
- 자가 점검 테스트 결과: **21 / 21 통과**
- 테스트 명령어: `.\mvnw.cmd clean test`

## 항목별 수정 내역

각 항목은 취약 원인, 수정 전 확인 내용, 수정 내용, 수정 후 확인 결과 순서로 정리하였다.

---

### [1] SQL 삽입(SQL Injection)

#### 취약 원인

MyBatis SQL에서 `${}`를 사용하면 전달된 값이 SQL 문장에 문자열로 직접 삽입된다. 예를 들어 고객 ID 조건에 `${customerId}`를 사용하면 공격자가 `' OR '1'='1`과 같은 값을 전달하여 조건을 항상 참으로 만들 수 있다. 그 결과 다른 고객의 대출 정보까지 조회될 위험이 있었다.

정렬 조건에 사용한 `ORDER BY ${sortBy}` 역시 입력값이 SQL에 직접 삽입되므로 허용되지 않은 SQL 구문이 전달될 위험이 있었다.

#### 공격 재현(수정 전)

요청:

```http
GET http://localhost:8080/api/loans?customerId=' OR '1'='1
```

결과: C001, C002, C003 등 여러 고객의 대출 정보가 함께 조회되는 것을 확인하였다.

정렬 조건에 잘못된 값을 전달했을 때도 SQL 오류가 발생하였다.

```http
GET /api/loans/sorted?customerId=C001&sortBy=nosuch
```

#### 수정 내용

1. 고객 ID 조건을 문자열 직접 삽입 방식에서 Prepared Statement 방식으로 변경하였다.

```xml
WHERE customer_id = #{customerId}
```

2. 정렬 기준은 SQL 바인딩만으로 처리하기 어려우므로 서비스에서 허용 목록(Whitelist)을 검사하도록 변경하였다.

```java
private static final Map<String, String> SORT_COLUMNS = Map.of(
    "loan_id", "loan_id",
    "balance", "balance",
    "status", "status"
);

public List<Loan> findSorted(String customerId, String sortBy) {
    String safeSortBy = SORT_COLUMNS.get(sortBy);

    if (safeSortBy == null) {
        throw new IllegalArgumentException("잘못된 정렬 기준입니다.");
    }

    return loanMapper.findSorted(customerId, safeSortBy);
}
```

정렬 SQL의 `${sortBy}`는 위 허용 목록을 통과한 값만 전달받도록 하였다.

#### 확인(수정 후)

- SQL 삽입 문자열을 고객 ID로 전달했을 때 다른 고객의 대출 정보가 조회되지 않는 것을 확인하였다.
- 허용되지 않은 정렬 기준 `nosuch`를 전달했을 때 `잘못된 정렬 기준입니다.` 예외가 발생하여 요청이 거부되는 것을 확인하였다.

---

### [2] 접근 통제(IDOR)

#### 취약 원인

대출 단건 조회 SQL이 대출 번호(`loan_id`)만으로 데이터를 조회하고 있었다. 로그인한 사용자인지 확인하더라도 SQL에서 소유 고객 ID를 함께 검사하지 않으면 다른 고객의 대출 번호를 이용해 타인의 대출 정보를 조회할 수 있었다.

#### 공격 재현(수정 전)

로그인한 고객이 C001인 상태에서 다른 고객의 대출 번호를 요청하였다.

```http
GET http://localhost:8080/api/loans/1002
```

결과: C002 소유의 대출 정보가 반환되는 것을 확인하였다.

#### 수정 내용

1. 대출 조회 SQL에 로그인한 고객 ID 조건을 추가하였다.

```xml
<select id="findById" resultType="edu.secure.assign.domain.Loan">
    SELECT loan_id, customer_id, balance, status
      FROM loan
     WHERE loan_id = #{loanId}
       AND customer_id = #{customerId}
</select>
```

2. 잔액 조회 및 수정 SQL에도 고객 ID 조건을 추가하여 소유자 확인이 함께 이루어지도록 하였다.

```xml
<select id="selectBalance" resultType="java.lang.Long">
    SELECT balance
      FROM loan
     WHERE loan_id = #{loanId}
       AND customer_id = #{customerId}
</select>

<update id="updateBalance">
    UPDATE loan
       SET balance = #{balance}
     WHERE loan_id = #{loanId}
       AND customer_id = #{customerId}
</update>
```

3. 컨트롤러에서는 세션에서 로그인한 고객 ID를 가져와 조회에 사용하도록 변경하였다.

```java
String me = LoginController.requireCustomer(session);
Loan result = loanMapper.findById(loanId, me);

if (result == null) {
    throw new ResponseStatusException(HttpStatus.NOT_FOUND);
}

return result;
```

Mapper 메서드에도 `@Param`을 사용하여 대출 번호와 고객 ID를 명확하게 전달하도록 하였다.

#### 확인(수정 후)

다른 고객 소유의 대출 번호를 요청했을 때 `404 NOT_FOUND`가 반환되는 것을 확인하였다.

**타인 자원 접근 시 404를 선택한 이유**

타인 소유의 대출 정보에 접근하거나 존재하지 않는 대출 정보를 요청한 경우 동일하게 HTTP 404(Not Found)를 반환하도록 하였다. 이를 통해 해당 대출 정보가 실제로 존재하는지 외부 사용자가 구분하기 어렵게 하여 정보 노출을 줄였다.

---

### [3] 민감정보 노출

#### 취약 원인

고객 정보 조회 API가 `Customer` 객체 전체를 반환하여 주민등록번호, 전화번호, 비밀번호 해시 등 불필요한 정보가 응답에 포함될 가능성이 있었다.

또한 `toString()` 메서드에서 고객 객체의 모든 필드를 출력하면 로그에 민감정보가 기록될 수 있었다.

#### 공격 재현(수정 전)

```http
GET http://localhost:8080/api/customers/C001
```

수정 전에는 다음과 같이 고객의 여러 필드가 응답에 포함되는 것을 확인하였다.

```json
{
  "customerId": "C001",
  "customerName": "홍길동",
  "rrn": "900101-1234567",
  "phone": "010-1111-2222",
  "passwordHash": null
}
```

#### 수정 내용

1. `Customer` 클래스의 주민등록번호, 전화번호, 비밀번호 해시 필드에 `@JsonIgnore`를 적용하여 JSON 응답에서 제외하였다.
2. `toString()` 메서드는 고객 ID와 이름만 출력하도록 변경하여 민감정보가 로그에 포함되지 않도록 하였다.
3. 고객 조회 시 세션의 로그인 고객 ID와 요청한 고객 ID가 일치하는지 확인하도록 하였다.
4. 컨트롤러가 `Customer` 엔티티를 직접 반환하지 않도록 `CustomerResponse` DTO를 추가하였다. DTO에는 고객 ID와 고객 이름만 포함하였다.

```java
public record CustomerResponse(
    String customerId,
    String customerName
) {
}
```

컨트롤러에서는 조회한 고객 정보 중 필요한 항목만 DTO에 담아 반환한다.

```java
return new CustomerResponse(
    c.getCustomerId(),
    c.getCustomerName()
);
```

#### 확인(수정 후)

```json
{
  "customerId": "C001",
  "customerName": "홍길동"
}
```

수정 후 응답에 고객 ID와 이름만 포함되고 주민등록번호, 전화번호, 비밀번호 해시는 포함되지 않는 것을 확인하였다.

---

### [4] 에러 처리

#### 취약 원인

기존 공통 예외 처리기에서 예외 객체의 상세 내용을 HTTP 응답에 그대로 반환하고 있었다. 이 경우 SQL 오류 메시지, 예외 클래스명, 서버 내부 경로 등 내부 정보가 외부 사용자에게 노출될 수 있었다.

#### 공격 재현(수정 전)

잘못된 금리 문자열을 전달하였다.

```http
GET /api/interest?principal=1000000&rate=abc
```

수정 전에는 `NumberFormatException`과 같은 내부 예외 정보가 응답에 노출될 수 있었다.

#### 수정 내용

1. 공통 예외 처리기(`@RestControllerAdvice`)를 적용하였다.
2. 서버 내부 예외의 상세 내용은 서버 로그에 기록하고, 사용자에게는 일반화된 오류 메시지만 반환하도록 변경하였다.
3. 잘못된 입력에 해당하는 `IllegalArgumentException`은 HTTP 400으로 처리하였다.
4. `ResponseStatusException`은 지정된 HTTP 상태 코드를 유지하도록 별도 처리하였다.

일반적인 서버 내부 오류 응답은 다음과 같이 반환하도록 하였다.

```json
{
  "error": "INTERNAL_SERVER_ERROR",
  "message": "서버 내부 오류가 발생했습니다."
}
```

#### 확인(수정 후)

잘못된 요청 및 내부 예외 상황에서 응답 본문에 SQL 문장이나 예외 상세 내용이 노출되지 않고 일반화된 오류 메시지가 반환되는 것을 확인하였다.

---

### [5] 입력값 검증

#### 취약 원인

대출 상환 금액과 이자 계산의 원금 및 금리에 대한 검증이 충분하지 않았다. 이로 인해 0 이하의 금액이나 음수 금리, 숫자가 아닌 문자열 등이 전달될 수 있었다.

#### 공격 재현(수정 전)

```http
GET /api/interest?principal=1000000&rate=-1
```

수정 전에는 음수 금리가 계산에 사용되어 `-83333`과 같은 잘못된 이자 결과가 반환되는 것을 확인하였다.

#### 수정 내용

1. 대출 상환 요청에서 대출 번호와 상환 금액이 0보다 큰지 검사하도록 하였다.
2. 이자 계산 요청에서 원금이 0보다 큰지 검사하도록 하였다.
3. 금리를 숫자로 변환할 수 있는지 검사하고, 숫자가 아니거나 유한한 값이 아니면 요청을 거부하도록 하였다.
4. 금리는 연이율 기준 `0 이상 1 이하`의 범위만 허용하도록 하였다.
5. 검증에 실패하면 HTTP 400(Bad Request)을 반환하도록 하였다.

#### 확인(수정 후)

정상 상환 요청:

```javascript
fetch("http://localhost:8080/api/loans/1001/repay?amount=10000", {
    method: "POST",
    credentials: "same-origin"
}).then(async r => {
    console.log("상태 코드:", r.status);
    console.log("응답:", await r.text());
});
```

결과:

```text
상태 코드: 200
응답: {"success":true}
```

음수 상환 요청:

```javascript
fetch("http://localhost:8080/api/loans/1001/repay?amount=-10000", {
    method: "POST",
    credentials: "same-origin"
}).then(async r => {
    console.log("상태 코드:", r.status);
    console.log("응답:", await r.text());
});
```

결과: HTTP 400(Bad Request)이 반환되어 잘못된 상환 금액이 거부되는 것을 확인하였다.

---

### [6] 비밀번호 해시 / 암호화 / 키 관리

#### 취약 원인

비밀번호를 안전한 단방향 해시 방식으로 저장하지 않으면 데이터베이스가 유출되었을 때 비밀번호가 노출될 위험이 있었다.

주민등록번호와 같은 복호화가 필요한 민감정보는 암호화하여 저장해야 하며, 암호화 키와 데이터베이스 접속 비밀번호를 소스 코드나 설정 파일에 직접 작성하면 저장소 유출 시 함께 노출될 위험이 있었다.

#### 수정 내용

**1. 비밀번호 해시**

기존 비밀번호 해시 방식을 BCrypt 기반으로 변경하였다. BCrypt는 비밀번호 검증을 위한 단방향 해시 방식이며, 저장된 해시값으로 원래 비밀번호를 복호화할 수 없다.

```java
private static final BCryptPasswordEncoder ENCODER =
        new BCryptPasswordEncoder();

public static String hash(String password) {
    return ENCODER.encode(password);
}

public static boolean verify(String password, String stored) {
    return ENCODER.matches(password, stored);
}
```

이를 위해 `spring-security-crypto` 의존성을 추가하였다.

**2. 주민등록번호 암호화**

주민등록번호는 AES/GCM/NoPadding 방식으로 암호화하도록 변경하였다. 암호화할 때마다 임의의 IV(초기화 벡터)를 생성하고, 암호문과 함께 사용하도록 하였다.

**3. 암호화 키 관리**

AES 키를 코드에 직접 작성하지 않고 `ASSIGN_AES_KEY` 환경 변수에서 Base64 형식으로 읽어오도록 변경하였다. 키가 없거나 형식 및 길이가 올바르지 않으면 정상적으로 암호화 기능을 사용할 수 없도록 검증하였다.

**4. 데이터베이스 접속 정보 관리**

데이터베이스 비밀번호도 설정 파일에 직접 작성하지 않고 `ASSIGN_DB_PASSWORD` 환경 변수에서 읽도록 변경하였다.

```properties
spring.datasource.password=${ASSIGN_DB_PASSWORD}
assign.aes-key=${ASSIGN_AES_KEY}
```

**5. 로그 정보 최소화**

회원가입 요청 로그에는 고객 ID만 기록하고 주민등록번호, 전화번호, 비밀번호 등의 입력값은 기록하지 않도록 변경하였다.

#### 확인(수정 후)

회원가입 테스트 데이터가 DB에 저장된 것을 확인하였다.

- 주민등록번호: 평문이 아닌 암호문 형태로 저장됨
- 비밀번호: `$2a$`로 시작하는 BCrypt 해시 형태로 저장됨

주민등록번호는 암호화된 값이므로 필요한 내부 처리에서 복호화할 수 있지만, 비밀번호는 단방향 해시이므로 복호화할 수 없다.

※ 확인한 DB 출력에서 전화번호는 평문으로 저장되어 있었다. 따라서 이번 수정에서 전화번호 암호화까지 완료했다고 보기는 어렵다.

---

### [7] 금액 계산 / 동시성

#### 취약 원인

금리 입력값에 대한 검증이 부족하면 음수 금리 등 잘못된 값으로 이자가 계산될 수 있었다.

또한 대출 상환 처리에서 잔액을 먼저 조회한 다음 애플리케이션에서 금액을 계산하고 UPDATE하는 방식은 여러 요청이 동시에 실행될 경우 잔액 확인 시점과 실제 수정 시점 사이에 경쟁 조건(Race Condition)이 발생할 수 있었다.

#### 공격 재현(수정 전)

```http
GET /api/interest?principal=1000000&rate=-1
```

결과: 음수 금리가 계산에 반영되어 `-83333`이 반환되는 것을 확인하였다.

#### 수정 내용

1. 이자 계산에서 원금이 양수인지 확인하고, 금리를 숫자로 변환할 수 있는지 및 허용 범위 안에 있는지 검사하도록 하였다.
2. 상환 금액이 0보다 큰지 검사하도록 하였다.
3. 상환 시 잔액을 먼저 조회한 후 별도로 수정하는 방식 대신, SQL 한 번으로 잔액 차감과 잔액 조건 검사를 함께 수행하도록 변경하였다.

```xml
<update id="repay">
    UPDATE loan
       SET balance = balance - #{amount}
     WHERE loan_id = #{loanId}
       AND customer_id = #{customerId}
       AND balance >= #{amount}
</update>
```

4. UPDATE 결과가 1건이면 상환 성공, 그렇지 않으면 실패로 처리하도록 하였다.

이 방식은 데이터베이스가 UPDATE를 수행하는 과정에서 잔액 조건을 함께 확인하므로, 조회와 수정이 분리된 기존 방식보다 동시 상환 상황에서 안전하다.

#### 확인(수정 후)

정상 상환 요청을 실행하여 HTTP 200과 `{"success":true}` 응답을 확인하였다.

또한 자가 점검 테스트에서 원자적 상환 SQL에 대한 검사(`repayIsAtomic`)가 통과하였다.

※ 별도의 다중 사용자 동시 요청 부하 테스트를 수행한 것은 아니며, 원자적 UPDATE 적용과 자동화 테스트를 통해 확인하였다.

---

### [8] XSS / CSRF

#### 취약 원인

**XSS**

검색어를 HTML에 직접 출력하거나 `innerHTML`로 삽입하면 사용자가 입력한 문자열이 HTML로 해석될 수 있다. 공격자가 `<script>` 태그 등을 입력하면 악성 스크립트가 실행될 위험이 있었다.

**CSRF**

상환 요청에 별도의 CSRF 토큰 검증이 없으면 사용자가 로그인한 상태에서 공격자가 만든 페이지를 방문했을 때 사용자의 의도와 관계없이 상환 요청이 전송될 수 있었다.

#### 공격 재현(수정 전)

**XSS**

검색어에 다음 문자열을 전달하는 방식으로 확인하였다.

```text
<script>alert(1)</script>
```

수정 전에는 입력값이 HTML로 해석될 가능성이 있었다.

**CSRF**

공격용 페이지에서 상환 요청을 전송할 때 세션 인증 정보만으로 요청이 처리될 수 있는 문제가 있었다.

#### 수정 내용

**1. XSS 방어**

검색어를 HTML로 해석하는 방식 대신 `textContent`로 화면에 출력하도록 변경하였다.

```javascript
const q = new URLSearchParams(location.search).get("q") || "";

document.getElementById("searchKeyword").textContent = q;
document.getElementById("searchKeywordAgain").textContent = q;

document.getElementById("summary").textContent =
    q + " 에 대한 검색 결과가 없습니다.";
```

**2. CSRF 방어**

- 상환 폼 페이지에 접근할 때 난수 기반 CSRF 토큰을 생성하도록 하였다.
- 생성한 토큰을 세션에 저장하고 JSP 상환 폼의 hidden input으로 전달하도록 하였다.
- 상환 요청이 들어오면 세션에 저장된 토큰과 요청으로 전달된 토큰을 비교하도록 하였다.
- 토큰이 없거나 일치하지 않으면 HTTP 403(Forbidden)을 반환하도록 하였다.

#### 확인(수정 후)

**XSS 확인**

검색어에 `<script>alert(1)</script>`를 입력하였다. 해당 문자열이 화면에 텍스트로 표시되고 실제 스크립트는 실행되지 않는 것을 확인하였다.

**CSRF 확인**

- 정상 상환 폼에서 토큰을 포함하여 요청했을 때 HTTP 200으로 정상 처리되는 것을 확인하였다.
- 공격용 페이지에서 CSRF 토큰 없이 상환 요청을 전송했을 때 HTTP 403(Forbidden)이 반환되는 것을 확인하였다.

---

## 자가 점검 테스트 결과

다음 명령어로 전체 테스트를 실행하였다.

```powershell
.\mvnw.cmd clean test
```

최종 결과:

```text
Tests run: 21, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

총 21개 테스트가 실행되었으며 실패 및 오류 없이 모두 통과하였다.

---

## 도구가 잡지 못한 항목(선택)

SpotBugs 및 Find Security Bugs 실행 결과는 별도로 기록하지 않았다.

## 추가로 발견한 문제(선택)

이번 수정 과정에서 고객 정보 응답에 불필요한 민감정보가 포함될 수 있는 점과 고객 객체의 로그 출력 문제를 확인하여 함께 개선하였다.

## 수업 내용과 다르게 적용한 부분 / 어려웠던 점

- MyBatis에서 `#{}`와 `${}`의 차이를 이해하고, 정렬 기준처럼 바인딩 변수만으로 처리하기 어려운 입력에는 허용 목록 검증을 적용하였다.
- IDOR는 로그인 여부만 확인해서 해결되는 문제가 아니라, 데이터 조회 및 수정 SQL에서도 로그인한 사용자의 소유권을 확인해야 한다는 점을 적용하였다.
- 비밀번호는 복호화가 필요 없는 BCrypt 단방향 해시로 저장하고, 주민등록번호는 복호화가 필요한 AES/GCM 암호화 방식으로 저장하도록 구분하였다.
- 상환 처리에서는 잔액 조회와 수정이 분리될 때 발생할 수 있는 경쟁 조건을 줄이기 위해 조건부 원자적 UPDATE를 적용하였다.
- XSS와 CSRF는 서로 다른 공격이므로, XSS에는 안전한 텍스트 출력 방식을 적용하고 CSRF에는 세션 기반 토큰 검증을 적용하였다.
