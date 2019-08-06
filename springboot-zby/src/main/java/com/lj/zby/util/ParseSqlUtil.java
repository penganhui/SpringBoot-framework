package com.lj.zby.util;

import java.io.*;

public class ParseSqlUtil {
    public static String resolveCode(String path) throws Exception {
        InputStream inputStream = new FileInputStream(path);
        byte[] head = new byte[3];
        inputStream.read(head);
        String code = "gb2312";  //或GBK
        if (head[0] == -1 && head[1] == -2)
            code = "UTF-16";
        else if (head[0] == -2 && head[1] == -1)
            code = "Unicode";
        else if (head[0] == -17 && head[1] == -69 && head[2] == -65)
            code = "UTF-8";

        inputStream.close();

        System.out.println(code);
        return code;
    }

    public static String readTxt(String path) {
        StringBuilder content = new StringBuilder("");
        try {
            String code = resolveCode(path);
            File file = new File(path);
            InputStream is = new FileInputStream(file);
            InputStreamReader isr = new InputStreamReader(is, code);
            BufferedReader br = new BufferedReader(isr);
            String str = "";
            while (null != (str = br.readLine())) {
                if (str.startsWith("INSERT")) {
                    content.append(str);
                    content.append("\n");
                }
            }
            br.close();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("读取文件:" + path + "失败!");
        }
        return content.toString();
    }

    public static void writeFileInfo(String context,String filePath) {
        /* 写入Txt文件 */
        try {
            File writename = new File(filePath); // 相对路径，如果没有则要建立一个新的output。txt文件
            writename.createNewFile(); // 创建新文件
            BufferedWriter out = new BufferedWriter(new FileWriter(writename));
            out.write(context); // \r\n即为换行
            out.flush(); // 把缓存区内容压入文件
            out.close(); // 最后记得关闭文件
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
//        StringBuilder command = new StringBuilder();
//        command.append("cmd /c mysqldump -hlocalhost -uroot -padmin gupao sys_job --where=\" job_name = 'eeee' \" > D:\\TableConditon.sql");
//        try {
//           String sql =  CommandUtil.run(command.toString());
//            System.out.println(sql);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        String pathname = "D:\\TableConditon.sql"; // 绝对路径或相对路径都可以，这里是绝对路径，写入文件时演示相对路径
        String content = readTxt(pathname);
        System.out.println(content);
        writeFileInfo(content,"D:\\output.txt");
    }
}
