package com.example.bitvibe;

public class PriceAlarm {
    private double triggerPrice;
    private boolean isAbove;
    private String currency;

    public PriceAlarm(double triggerPrice, boolean isAbove, String currency) {
        this.triggerPrice = triggerPrice;
        this.isAbove = isAbove;
        this.currency = currency;
    }

    public double getTriggerPrice() {
        return triggerPrice;
    }

    public boolean isAbove() {
        return isAbove;
    }

    public String getCurrency() {
        return currency;
    }
    public void setTriggerPrice(double triggerPrice){
        this.triggerPrice=triggerPrice;
    }
    public void setCurrency(String currency){
        this.currency=currency;
    }
    public void setIsAbove(boolean isAbove){
        this.isAbove=isAbove;
    }

}