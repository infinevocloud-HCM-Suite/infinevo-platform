package com.infinevo.payroll.statutory.lwf;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LwfStateRepository extends JpaRepository<LwfState, String> {

    List<LwfState> findByStateCodeIn(List<String> stateCodes);
}
