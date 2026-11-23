package com.example.finance.form;

import com.example.finance.model.EntryType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class CategoryForm {

    @NotBlank(message = "請輸入分類名稱")
    @Size(max = 30)
    private String name;

    @NotNull
    private EntryType type = EntryType.EXPENSE;

    @Size(max = 10)
    private String icon = "";

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public EntryType getType() { return type; }
    public void setType(EntryType type) { this.type = type; }
    public String getIcon() { return icon; }
    public void setIcon(String icon) { this.icon = icon; }
}
