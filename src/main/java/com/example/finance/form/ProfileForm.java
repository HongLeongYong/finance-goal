package com.example.finance.form;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalTime;
import org.springframework.format.annotation.DateTimeFormat;

public class ProfileForm {

    @NotNull(message = "請輸入月薪")
    @DecimalMin(value = "0", message = "月薪不能是負的")
    private BigDecimal monthlySalary;

    @Min(value = 1, message = "至少 1 天")
    @Max(value = 31, message = "最多 31 天")
    private int workDaysPerMonth = 22;

    @NotNull(message = "請輸入上班時間")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime workStartTime;

    @NotNull(message = "請輸入下班時間")
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime workEndTime;

    /** 午休可不填；要填就兩個都填 */
    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime breakStartTime;

    @DateTimeFormat(pattern = "HH:mm")
    private LocalTime breakEndTime;

    /** 券商手續費折扣：0.6 = 6 折 */
    @NotNull(message = "請輸入手續費折扣")
    @DecimalMin(value = "0.1", message = "折扣最低 0.1（1 折）")
    @jakarta.validation.constraints.DecimalMax(value = "1", message = "折扣最高 1（不打折）")
    private BigDecimal feeDiscount = new BigDecimal("0.6");

    @Min(value = 0, message = "最低手續費不能是負的")
    @Max(value = 100, message = "最低手續費最多 100 元")
    private int minFee = 20;

    public BigDecimal getFeeDiscount() { return feeDiscount; }
    public void setFeeDiscount(BigDecimal feeDiscount) { this.feeDiscount = feeDiscount; }
    public int getMinFee() { return minFee; }
    public void setMinFee(int minFee) { this.minFee = minFee; }

    @AssertTrue(message = "下班時間要晚於上班時間")
    public boolean isWorkPeriodValid() {
        return workStartTime == null || workEndTime == null || workEndTime.isAfter(workStartTime);
    }

    @AssertTrue(message = "午休要同時填開始和結束，結束要晚於開始，並且落在上下班時間內")
    public boolean isBreakValid() {
        if (breakStartTime == null && breakEndTime == null) {
            return true;
        }
        if (breakStartTime == null || breakEndTime == null || !breakEndTime.isAfter(breakStartTime)) {
            return false;
        }
        return workStartTime == null || workEndTime == null
                || (!breakStartTime.isBefore(workStartTime) && !breakEndTime.isAfter(workEndTime));
    }

    public BigDecimal getMonthlySalary() { return monthlySalary; }
    public void setMonthlySalary(BigDecimal monthlySalary) { this.monthlySalary = monthlySalary; }
    public int getWorkDaysPerMonth() { return workDaysPerMonth; }
    public void setWorkDaysPerMonth(int workDaysPerMonth) { this.workDaysPerMonth = workDaysPerMonth; }
    public LocalTime getWorkStartTime() { return workStartTime; }
    public void setWorkStartTime(LocalTime workStartTime) { this.workStartTime = workStartTime; }
    public LocalTime getWorkEndTime() { return workEndTime; }
    public void setWorkEndTime(LocalTime workEndTime) { this.workEndTime = workEndTime; }
    public LocalTime getBreakStartTime() { return breakStartTime; }
    public void setBreakStartTime(LocalTime breakStartTime) { this.breakStartTime = breakStartTime; }
    public LocalTime getBreakEndTime() { return breakEndTime; }
    public void setBreakEndTime(LocalTime breakEndTime) { this.breakEndTime = breakEndTime; }
}
