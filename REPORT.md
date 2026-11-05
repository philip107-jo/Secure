# 시큐어 코딩 적용 보고서

- 이름:
- 제출일:
- 자가 점검 테스트 결과: `__ / 21` 통과

## 항목별 수정 내역

항목마다 **취약 원인 → 공격 재현(수정 전) → 수정 내용 → 확인(수정 후)** 순서로 적는다.
수정하지 못한 항목은 비워 두지 말고 이유와 시도한 내용을 적는다.

### [1] SQL 삽입
1
- 취약 원인:WHERE customer_id = '${customerId}'는 전달받은 값 그대로 sql문자열에 삽입한다. 따라서 ' OR '1'='1 과같은 값을 전달하면 항상 참이기때문에 다른고객의 대출정보까지 조회될수있다.
- 공격 재현(수정 전): (호출한 요청과 결과:요청: http://localhost:8080/api/loans?customerId=' OR '1'='1
결과:[
{
"loanId": 1001,
"customerId": "C001",
"balance": 12500000,
"status": "ACTIVE"
},
{
"loanId": 1002,
"customerId": "C002",
"balance": 30000000,
"status": "ACTIVE"
},
{
"loanId": 1003,
"customerId": "C003",
"balance": 5000000,
"status": "ACTIVE"
},
{
"loanId": 1004,
"customerId": "C001",
"balance": 8000000,
"status": "ACTIVE"
}
])
- 수정 내용:   WHERE customer_id = #{customerId}
- 확인(수정 후):[] ->정보가 나오지않음



2
- 취약 원인:ORDER BY ${sortBy} ->${sortBy}는 입력값을 그대로 SQL에 넣는다.그래서 공격자가 악의적인 sql구문을 삽입할수있음 정렬을 마음대로 바꾸거나하는 코드로
- 공격 재현(수정 전): (호출한 요청과 결과:요청: /api/loans/sorted?customerId=C001&sortBy=nosuch  결과:org.springframework.jdbc.BadSqlGrammarException: ### Error querying database. Cause: java.sql.SQLSyntaxErrorException: ORA-00904: "NOSUCH": 부적합한 식별자 ### The error may exist in file [C:\workspace\secure-coding-assignment\target\classes\mapper\LoanMapper.xml] ### The error may involve defaultParameterMap ### The error occurred while setting parameters ### SQL: SELECT loan_id, customer_id, balance, status FROM loan WHERE customer_id = ? ORDER BY nosuch ### Cause: java.sql.SQLSyntaxErrorException: ORA-00904: "NOSUCH": 부적합한 식별자 ; bad SQL grammar []
- 수정 내용:       public List<Loan> findSorted(String customerId, String sortBy) {

        // 허용 목록에 존재하는지 검사
        String safeSortBy = SORT_COLUMNS.get(sortBy);

        if (safeSortBy == null) {
            throw new IllegalArgumentException("잘못된 정렬 기준입니다.");
        }

        return loanMapper.findSorted(customerId, safeSortBy);
    }
- 확인(수정 후):java.lang.IllegalArgumentException: 잘못된 정렬 기준입니다.
### [2] 접근 통제 (IDOR)

- 취약 원인: <select id="findById" resultType="edu.secure.assign.domain.Loan">
        SELECT loan_id, customer_id, balance, status
          FROM loan
         WHERE loan_id = #{loanId}
    </select> ->대출번호만 일치한다면 로그인번호가 달라도 남의정보를 볼수있음
- 공격 재현(수정 전):요청:http://localhost:8080/api/loans/1002
결과:{
"loanId": 1002,
"customerId": "C002",
"balance": 30000000,
"status": "ACTIVE"
}
- 수정 내용:    <select id="findById" resultType="edu.secure.assign.domain.Loan">
        SELECT loan_id, customer_id, balance, status
          FROM loan
         WHERE loan_id = #{loanId}
         AND customer_id=#{customerId}
    </select>

    <select id="selectBalance" resultType="java.lang.Long">
        SELECT balance FROM loan WHERE loan_id = #{loanId} AND customer_id = #{customerId}
    </select>

    <update id="updateBalance">
        UPDATE loan SET balance = #{balance} WHERE loan_id = #{loanId}    AND customer_id = #{customerId}
    </update>
    @GetMapping("/loans/{loanId}")
public Loan loan(@PathVariable long loanId, HttpSession session) {

    String me = LoginController.requireCustomer(session);

    Loan result = loanMapper.findById(loanId, me);

    if (result == null) {
        throw new ResponseStatusException(
            HttpStatus.NOT_FOUND
        );
    }

    return result;
}
Loan findById(
    @Param("loanId") long loanId,
    @Param("customerId") String customerId
);

Long selectBalance(
    @Param("loanId") long loanId,
    @Param("customerId") String customerId
);

int updateBalance(
    @Param("loanId") long loanId,
    @Param("customerId") String customerId,
    @Param("balance") long balance
);
- 확인(수정 후):org.springframework.web.server.ResponseStatusException: 404 NOT_FOUND
- 타인 자원 접근 시 응답 코드를 무엇으로 정했고, 그 이유는?타인 소유의 대출 정보에 접근하거나 존재하지 않는 대출 정보를 요청한 경우 HTTP 404(Not Found)를 반환하도록 설정하였다. 이를 통해 두 상황의 응답을 동일하게 유지하여 타인의 대출 정보 존재 여부가 외부에 노출되지 않도록 하였다.

### [3] 민감정보 노출

- 취약 원인: 고객 정보 조회 시 Customer 객체 전체를 API 응답으로 반환하여 주민등록번호, 전화번호, 비밀번호 해시 등의 민감정보가 외부에 노출될 가능성이 있었다. 또한 toString() 메서드에서 고객 객체의 모든 필드를 출력하도록 구현되어 있어 로그에도 민감정보가 기록될 수 있는 문제가 있었다.
- 공격 재현(수정 전):http://localhost:8080/api/customers/C001->{
"customerId": "C001",
"customerName": "홍길동",
"rrn": "900101-1234567",
"phone": "010-1111-2222",
"passwordHash": null
}
- 수정 내용:    @GetMapping("/customers/{customerId}")
    public Customer customer(@PathVariable String customerId, HttpSession session) {

        String me = LoginController.requireCustomer(session);

        if (!me.equals(customerId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        Customer c = customerMapper.findById(me);

        log.info("고객 조회 요청: {}", me);

        if (c == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        return c;
    }
    package edu.secure.assign.domain;
import com.fasterxml.jackson.annotation.JsonIgnore;
/** DB 원본 객체 */
public class Customer {
	
	@JsonIgnore
	private String rrn;
	
	@JsonIgnore
	private String phone;
	
	@JsonIgnore
	private String passwordHash;

    private String customerId;
    private String customerName;
    public Customer() {
    }

    public Customer(String customerId, String customerName, String rrn, String phone, String passwordHash) {
        this.customerId = customerId;
        this.customerName = customerName;
        this.rrn = rrn;
        this.phone = phone;
        this.passwordHash = passwordHash;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getRrn() { return rrn; }
    public void setRrn(String rrn) { this.rrn = rrn; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    @Override
    public String toString() {
        return "Customer{customerId='" + customerId
                + "', customerName='" + customerName + "'}";
    }
}

- 확인(수정 후):{
"customerId": "C001",
"customerName": "홍길동"
}

### [4] 에러 처리

- 취약 원인:공통 예외 처리기에서 예외 객체의 toString() 결과를 HTTP 응답 본문에 그대로 반환하도록 구현되어 있었다. 이로 인해 SQL 오류 메시지, 예외 클래스명 등 서버 내부 정보가 외부 사용자에게 노출될 가능성이 있었다.
- 공격 재현(수정 전):GET /api/interest?principal=1000000&rate=abc-> java.lang.NumberFormatException: For input string: "abc"
- 수정 내용:  
package edu.secure.assign.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handle(Exception e) {

        log.error("서버 내부 오류 발생", e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of(
                        "error", "INTERNAL_SERVER_ERROR",
                        "message", "서버 내부 오류가 발생했습니다."
                ));
    }
}
- 확인(수정 후):
{
"message": "서버 내부 오류가 발생했습니다.",
"error": "INTERNAL_SERVER_ERROR"
}

### [5] 입력값 검증

- 취약 원인:대출 원금 및 상환 금액에 대한 유효성 검사가 충분하지 않아 0 이하의 값이 입력될 수 있었다.

금리 입력값을 숫자로 변환하는 과정에서 잘못된 문자열이나 허용 범위를 벗어난 값에 대한 검증이 부족했다.

잘못된 입력으로 예외가 발생할 경우 내부 오류 정보가 응답에 노출될 가능성이 있었다
- 수정 내용: (원금이 0 이하인 경우 요청을 거부하도록 구현하였다.



금리는 숫자로 변환 가능한 값인지 검사하였다.



금리가 0~1 범위를 벗어나면 HTTP 400을 반환하도록 처리하였다.



잘못된 입력값에 내부 예외 정보가 노출되지 않도록 공통 예외 처리기를 개선하였다.
- 확인(수정 후): fetch("http://localhost:8080/api/loans/1001/repay?amount=10000", {
    method: "POST",
    credentials: "same-origin"
}).then(async r => {
    console.log("상태 코드:", r.status);
    console.log("응답:", await r.text());
});
Promise {<pending>}
VM92:5 상태 코드: 200
VM92:6 응답: {"success":true}
fetch("http://localhost:8080/api/loans/1001/repay?amount=-10000", {
    method: "POST",
    credentials: "same-origin"
}).then(async r => {
    console.log("상태 코드:", r.status);
    console.log("응답:", await r.text());
});
Promise {<pending>}
VM96:1  POST http://localhost:8080/api/loans/1001/repay?amount=-10000 400 (Bad Request)
(anonymous) @ VM96:1
VM96:5 상태 코드: 400
VM96:6 응답: {"error":"BAD

### [6] 비밀번호 해시 / 암호화 / 키 관리

- 취약 원인: (해시, 암호화, DB 접속 정보, 키 각각)
- 수정 내용:
- 확인(수정 후):

### [7] 금액 계산 / 동시성

- 취약 원인: (이자 계산, 상환 금액 검증, 경쟁 조건 각각)
- 공격 재현(수정 전): (음수 상환 결과, 동시 요청 결과 등)
- 수정 내용:
- 확인(수정 후):

### [8] XSS / CSRF

- 취약 원인:
- 공격 재현(수정 전):
- 수정 내용:
- 확인(수정 후): (`attack/csrf-attack.html` 실행 결과 포함)

## 도구가 잡지 못한 항목 (선택)

SpotBugs + Find Security Bugs 를 실행했다면, 수정 전/후 결과와 도구가 잡지 못한 항목을 적는다.

## 추가로 발견한 문제 (선택)

위 8개 항목 외에 발견한 보안 약점과 조치 내용.

## 수업 내용과 다르게 적용한 부분 / 어려웠던 점
