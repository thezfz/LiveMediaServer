package com.example.rtmpserver;

import java.io.DataOutputStream;
import java.io.IOException;

/**
 * (推荐) AMF0编码/解码的辅助工具类.
 * 包含了将Java数据类型序列化为AMF0格式字节流的常用方法。
 * 这将极大地简化在RtmpHandler中构造响应消息的复杂度。
 */
public class Amf0Utils {

    /**
     * 写入一个AMF0 String标记和值.
     * AMF0 Type: 0x02
     * @param out 输出流
     * @param value 要写入的字符串
     */
    public static void writeString(DataOutputStream out, String value) throws IOException {
        out.writeByte(0x02); // Type: String
        out.writeShort(value.length());
        out.writeBytes(value);
    }

    /**
     * 写入一个AMF0 Number标记和值.
     * AMF0 Type: 0x00
     * @param out 输出流
     * @param value 要写入的double值
     */
    public static void writeNumber(DataOutputStream out, double value) throws IOException {
        out.writeByte(0x00); // Type: Number
        out.writeDouble(value);
    }
    
    /**
     * 写入一个AMF0 Object的起始标记.
     * AMF0 Type: 0x03
     * @param out 输出流
     */
    public static void writeObjectBegin(DataOutputStream out) throws IOException {
        out.writeByte(0x03); // Type: Object
    }
    
    /**
     * 写入一个AMF0 Object的结束标记.
     * AMF0 Type: 0x09
     * @param out 输出流
     */
    public static void writeObjectEnd(DataOutputStream out) throws IOException {
        out.writeShort(0); // key_length = 0, a virtual key of empty string
        out.writeByte(0x09); // End of Object marker
    }
    
    /**
     * 写入一个AMF0 Object的属性 (Key-Value对)，其中值为Number类型.
     * @param out 输出流
     * @param key 属性名
     * @param value 属性值 (double)
     */
    public static void writeObjectProperty(DataOutputStream out, String key, double value) throws IOException {
        out.writeShort(key.length());
        out.writeBytes(key);
        writeNumber(out, value); // The value is a Number type
    }
    
    /**
     * 写入一个AMF0 Object的属性 (Key-Value对)，其中值为String类型.
     * @param out 输出流
     * @param key 属性名
     * @param value 属性值 (String)
     */
    public static void writeObjectProperty(DataOutputStream out, String key, String value) throws IOException {
        out.writeShort(key.length());
        out.writeBytes(key);
        // Note: The value itself is a String type, so we need to write the string marker first.
        writeString(out, value);
    }

    /**
     * 写入一个AMF0 Null标记.
     * AMF0 Type: 0x05
     * @param out 输出流
     */
    public static void writeNull(DataOutputStream out) throws IOException {
        out.writeByte(0x05); // Type: Null
    }
} 