<%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
<form action="${pageContext.request.contextPath}/repay-form" method="post">

    <input type="hidden" name="_csrf" value="${csrfToken}">

    대출번호 <input name="loanId" value="1001">

    금액 <input name="amount" value="1000">

    <button type="submit">상환</button>

</form>