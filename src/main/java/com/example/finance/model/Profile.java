package com.example.finance.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** 個人設定，只有一筆（id = 1）。 */
@Entity
public class Profile {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    @Column(name = "monthly_salary", nullable = false, precision = 15, scale = 2)
    private BigDecimal monthlySalary = BigDecimal.ZERO;

    @Column(name = "work_days_per_month", nullable = false)
    private int workDaysPerMonth = 22;

    /** 由上下班時間與午休自動算出，存起來方便查詢 */
    @Column(name = "work_hours_per_day", nullable = false, precision = 5, scale = 2)
    private BigDecimal workHoursPerDay = BigDecimal.valueOf(8);

    @Column(name = "work_start_time", nullable = false)
    private LocalTime workStartTime = LocalTime.of(9, 0);

    @Column(name = "work_end_time", nullable = false)
    private LocalTime workEndTime = LocalTime.of(17, 0);

    /** 午休開始；null = 沒有午休 */
    @Column(name = "break_start_time")
    private LocalTime breakStartTime;

    @Column(name = "break_end_time")
    private LocalTime breakEndTime;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
    /** 券商手續費折扣，0.6 = 6 折 */
    @Column(name = "fee_discount", nullable = false, precision = 4, scale = 2)
    private BigDecimal feeDiscount = new BigDecimal("0.60");

    /** 整股最低手續費（零股固定最低 1 元） */
    @Column(name = "min_fee", nullable = false)
    private int minFee = 20;

    public BigDecimal getFeeDiscount() { return feeDiscount; }
    public void setFeeDiscount(BigDecimal feeDiscount) { this.feeDiscount = feeDiscount; }
    public int getMinFee() { return minFee; }
    public void setMinFee(int minFee) { this.minFee = minFee; }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }
    public BigDecimal getMonthlySalary() { return monthlySalary; }
    public void setMonthlySalary(BigDecimal monthlySalary) { this.monthlySalary = monthlySalary; }
    public int getWorkDaysPerMonth() { return workDaysPerMonth; }
    public void setWorkDaysPerMonth(int workDaysPerMonth) { this.workDaysPerMonth = workDaysPerMonth; }
    public BigDecimal getWorkHoursPerDay() { return workHoursPerDay; }
    public void setWorkHoursPerDay(BigDecimal workHoursPerDay) { this.workHoursPerDay = workHoursPerDay; }
    public LocalTime getWorkStartTime() { return workStartTime; }
    public void setWorkStartTime(LocalTime workStartTime) { this.workStartTime = workStartTime; }
    public LocalTime getWorkEndTime() { return workEndTime; }
    public void setWorkEndTime(LocalTime workEndTime) { this.workEndTime = workEndTime; }
    public LocalTime getBreakStartTime() { return breakStartTime; }
    public void setBreakStartTime(LocalTime breakStartTime) { this.breakStartTime = breakStartTime; }
    public LocalTime getBreakEndTime() { return breakEndTime; }
    public void setBreakEndTime(LocalTime breakEndTime) { this.breakEndTime = breakEndTime; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
