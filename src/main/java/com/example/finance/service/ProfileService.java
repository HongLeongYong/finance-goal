package com.example.finance.service;

import com.example.finance.form.ProfileForm;
import com.example.finance.model.Profile;
import com.example.finance.repository.ProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {

    private final ProfileRepository profileRepository;

    public ProfileService(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Transactional(readOnly = true)
    public Profile get() {
        return profileRepository.findById(Profile.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException("profile 資料不存在，請確認 Flyway migration 有執行"));
    }

    @Transactional(readOnly = true)
    public PayRate payRate() {
        Profile p = get();
        return new PayRate(p.getMonthlySalary(), p.getWorkDaysPerMonth(), p.getWorkStartTime(), p.getWorkEndTime(),
                p.getBreakStartTime(), p.getBreakEndTime());
    }

    @Transactional
    public void update(ProfileForm form) {
        Profile p = get();
        p.setMonthlySalary(form.getMonthlySalary());
        p.setWorkDaysPerMonth(form.getWorkDaysPerMonth());
        p.setWorkStartTime(form.getWorkStartTime());
        p.setWorkEndTime(form.getWorkEndTime());
        p.setBreakStartTime(form.getBreakStartTime());
        p.setBreakEndTime(form.getBreakEndTime());
        p.setFeeDiscount(form.getFeeDiscount());
        p.setMinFee(form.getMinFee());
        long minutes = PayRate.workMinutesPerDay(form.getWorkStartTime(), form.getWorkEndTime(),
                form.getBreakStartTime(), form.getBreakEndTime());
        p.setWorkHoursPerDay(BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));
        p.setUpdatedAt(LocalDateTime.now());
        profileRepository.save(p);
    }

    public static ProfileForm toForm(Profile p) {
        ProfileForm f = new ProfileForm();
        f.setMonthlySalary(p.getMonthlySalary());
        f.setWorkDaysPerMonth(p.getWorkDaysPerMonth());
        f.setWorkStartTime(p.getWorkStartTime());
        f.setWorkEndTime(p.getWorkEndTime());
        f.setBreakStartTime(p.getBreakStartTime());
        f.setBreakEndTime(p.getBreakEndTime());
        f.setFeeDiscount(p.getFeeDiscount());
        f.setMinFee(p.getMinFee());
        return f;
    }
}
