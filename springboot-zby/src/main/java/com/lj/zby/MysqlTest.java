package com.lj.zby;

import com.mysql.jdbc.PreparedStatement;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

public class MysqlTest {
    //数据库地址
    private static final String url = "jdbc:mysql://localhost:3306/gupao";
    private static final String name = "com.mysql.jdbc.Driver";
    private static final String username = "root";
    private static final String password = "admin";
    private Connection connection= null;
    private PreparedStatement preparedStatement = null;
    private MysqlTest(String sql){
        try{
            Class.forName(name);
            connection = DriverManager.getConnection(url, username, password);
            preparedStatement = (PreparedStatement) connection.prepareStatement(sql);

        }catch(Exception e){
            e.printStackTrace();
        }
    }
    private void close(){
        try{
            this.connection.close();
            this.preparedStatement.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }
    public static void main(String[] args){
        String sql = "SELECT * FROM sys_job";
        MysqlTest dbManager = new MysqlTest(sql);  //实例化

        String id, name, gender, phone, email, description;

        try{
            ResultSet result = dbManager.preparedStatement.executeQuery();
            while(result.next()){                  //若有数据，就输出
                id = result.getString(1);
                name = result.getString(2);
                gender = result.getString(3);
                phone = result.getString(4);
                email = result.getString(5);
                description = result.getString(6);
                //显示出每一行数据
                System.out.println(id + "  " + name + "  " + gender + "  "
                        + phone + "  " + email + "  " + description);
            }
            result.close();
            dbManager.close();

        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
