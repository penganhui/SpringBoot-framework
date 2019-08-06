package com.lj.zby.util;

import java.io.*;
import java.util.concurrent.TimeUnit;

public class CommandUtil {
    public static String run(String command) throws IOException {
        String result = "";
        Process process = null;
        try {
            try {
                process = Runtime.getRuntime().exec(command);
                //等待命令执行完成
                process.waitFor(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result = command + "\n" + result; //加上命令本身，打印出来
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result;
    }

    public static String run(String[] command) throws IOException {
        String result = "";
        Process process = null;
        try {
            process = Runtime.getRuntime().exec(command);
            try {
                //等待命令执行完成
                process.waitFor(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            result = command + "\n" + result; //加上命令本身，打印出来
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
        return result;
    }


}
