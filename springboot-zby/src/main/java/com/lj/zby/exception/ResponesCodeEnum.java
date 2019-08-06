package com.lj.zby.exception;

public enum ResponesCodeEnum {
    SCCUESS("0","interface return sccuessfully")
    ;
    private String code;
    private String msg;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }

    ResponesCodeEnum(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }
}
