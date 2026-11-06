
package edu.secure.assign.service;

import edu.secure.assign.domain.Loan;
import edu.secure.assign.mapper.LoanMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class LoanService {

    private final LoanMapper loanMapper;

    // 허용된 정렬 기준만 지정
    private static final Map<String, String> SORT_COLUMNS = Map.of(
        "loan_id", "loan_id",
        "balance", "balance",
        "status", "status"
    );

    public LoanService(LoanMapper loanMapper) {
        this.loanMapper = loanMapper;
    }

    /** 정렬 기준(sortBy)으로 대출 목록 조회 */
    public List<Loan> findSorted(String customerId, String sortBy) {

        // 허용 목록에 존재하는지 검사
        String safeSortBy = SORT_COLUMNS.get(sortBy);

        if (safeSortBy == null) {
            throw new IllegalArgumentException("잘못된 정렬 기준입니다.");
        }

        return loanMapper.findSorted(customerId, safeSortBy);
    }

    /** 대출 상환. 잔액이 부족하면 false */
    public boolean repay(long loanId, String customerId, long amount) {

        if (loanId <= 0 || amount <= 0) {
            throw new IllegalArgumentException("잘못된 대출 ID 또는 상환 금액입니다.");
        }

        int result = loanMapper.repay(loanId, customerId, amount);

        return result == 1;
    }

    /** 월 이자 = 원금 × 연이율 ÷ 12 (원 단위 반올림) */
    public long monthlyInterest(long principal, String annualRate) {

        if (principal <= 0) {
            throw new IllegalArgumentException("원금은 0보다 커야 합니다.");
        }

        double rate;

        try {
            rate = Double.parseDouble(annualRate);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("금리 형식이 올바르지 않습니다.");
        }

        if (!Double.isFinite(rate) || rate < 0 || rate > 1) {
            throw new IllegalArgumentException("금리는 0~1 사이여야 합니다.");
        }

        return Math.round(principal * rate / 12);
    }
}
