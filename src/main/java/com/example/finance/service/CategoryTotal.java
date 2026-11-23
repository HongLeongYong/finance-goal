package com.example.finance.service;

import java.math.BigDecimal;

/** 某分類在一段期間的金額合計（JPQL constructor expression 用） */
public record CategoryTotal(Long categoryId, String name, String icon, BigDecimal amount) {
}
