package com.joey.myvoice.util;

import java.security.MessageDigest;

/**
 * Created by Tomy on 2017/2/17 0017.
 * 备注：MD5摘要工具类
 */

public class MD5Util {

    public static String md5(String str) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(str.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            byte[] buf = md5.digest();
            int i;
            for (byte b : buf) {
                i = b;
                if (i < 0)
                    i += 256;
                if (i < 16)
                    sb.append("0");
                sb.append(Integer.toHexString(i));
            }
            return sb.toString();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }
}

