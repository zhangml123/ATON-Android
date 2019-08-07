package com.juzix.wallet.entity;

import android.text.TextUtils;

public class DelegateType {
    private String type;
    private double amount;

    private String address;

    public DelegateType(String type, double amount, String address) {
        this.type = type;
        this.amount = amount;
        this.address = address;
    }


    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public double getAmount() {
        return amount;
    }

    public void setAmount(double amount) {
        this.amount = amount;
    }

    @Override
    public int hashCode() {
        return TextUtils.isEmpty(address) ? 0 : address.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof Wallet) {
            Wallet entity = (Wallet) obj;
            return entity.getUuid() != null && entity.getUuid().equals(address);
        }
        return super.equals(obj);
    }
}