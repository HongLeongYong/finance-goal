package com.example.finance.repository;

import com.example.finance.model.Category;
import com.example.finance.model.EntryType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findByTypeOrderBySortOrderAscIdAsc(EntryType type);

    List<Category> findAllByOrderByTypeAscSortOrderAscIdAsc();

    boolean existsByTypeAndName(EntryType type, String name);
}
