package edu.secure.assign.mapper;

import edu.secure.assign.domain.Loan;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface LoanMapper {

    List<Loan> findByCustomer(@Param("customerId") String customerId);

    List<Loan> findSorted(@Param("customerId") String customerId, @Param("sortBy") String sortBy);
    Loan findById(
    	    @Param("loanId") long loanId,
    	    @Param("customerId") String customerId
    	);

    int repay(
    	    @Param("loanId") long loanId,
    	    @Param("customerId") String customerId,
    	    @Param("amount") long amount
    	);
}
