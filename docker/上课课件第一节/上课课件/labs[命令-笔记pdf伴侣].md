**MySQL数据库集群**

```
01 拉取pxc镜像
	docker pull percona/percona-xtradb-cluster:5.7.21
	
02 复制pxc镜像(实则重命名)
	docker tag percona/percona-xtradb-cluster:5.7.21 pxc

03 删除pxc原来的镜像
	docker rmi percona/percona-xtradb-cluster:5.7.21
	
04 创建一个单独的网段，给mysql数据库集群使用
	(1)docker network create --subnet=172.18.0.0/24 pxc-net
	(2)docket network inspect pxc-net   [查看详情]
	(3)docker network rm pxc-net        [删除]

05 创建和删除volume
	创建：docker volume create --name v1
	删除：docker volume rm v1
	查看详情：docker volume inspect v1

06 创建单个PXC容器demo
	[CLUSTER_NAME PXC集群名字]
    [XTRABACKUP_PASSWORD数据库同步需要用到的密码]
    
	docker run -d -p 3301:3306 
	-v v1:/var/lib/mysql
	-e MYSQL_ROOT_PASSWORD=jack123
	-e CLUSTER_NAME=PXC
	-e XTRABACKUP_PASSWORD=jack123
	--privileged --name=node1 --net=pxc-net --ip 172.18.0.2
	pxc
	
07 搭建PXC[MySQL]集群
	(1)准备3个数据卷
		docker volume create --name v1
		docker volume create --name v2
		docker volume create --name v3
	(2)运行三个PXC容器
	    【在创建完第一个node1，需要等待一段时间，大概1分钟左右，等node1启动初始化完成，才能创建node2和node3，不然会出错，大家一定要注意哦】
		docker run -d -p 3301:3306 -v v1:/var/lib/mysql -e MYSQL_ROOT_PASSWORD=jack123 -e CLUSTER_NAME=PXC -e XTRABACKUP_PASSWORD=jack123 --privileged --name=node1 --net=pxc-net --ip 172.18.0.2 pxc
		
		[CLUSTER_JOIN将该数据库加入到某个节点上组成集群]
		docker run -d -p 3302:3306 -v v2:/var/lib/mysql -e MYSQL_ROOT_PASSWORD=jack123 -e CLUSTER_NAME=PXC -e XTRABACKUP_PASSWORD=jack123 -e CLUSTER_JOIN=node1 --privileged --name=node2 --net=pxc-net --ip 172.18.0.3 pxc
		
		docker run -d -p 3303:3306 -v v3:/var/lib/mysql -e MYSQL_ROOT_PASSWORD=jack123 -e CLUSTER_NAME=PXC -e XTRABACKUP_PASSWORD=jack123 -e CLUSTER_JOIN=node1 --privileged --name=node3 --net=pxc-net --ip 172.18.0.4 pxc
	(3)MySQL工具连接测试
		Jetbrains Datagrip
```







**增加负载均衡**

> (1)拉取haproxy镜像

```
docker pull haproxy
```

> (2)创建haproxy配置文件，这里使用bind mounting的方式

```
touch /tmp/haproxy/haproxy.cfg
```

`haproxy.cfg`

```
global
	#工作目录，这边要和创建容器指定的目录对应
	chroot /usr/local/etc/haproxy
	#日志文件
	log 127.0.0.1 local5 info
	#守护进程运行
	daemon

defaults
	log	global
	mode	http
	#日志格式
	option	httplog
	#日志中不记录负载均衡的心跳检测记录
	option	dontlognull
 	#连接超时（毫秒）
	timeout connect 5000
 	#客户端超时（毫秒）
	timeout client  50000
	#服务器超时（毫秒）
 	timeout server  50000

    #监控界面	
    listen  admin_stats
	#监控界面的访问的IP和端口
	bind  0.0.0.0:8888
	#访问协议
 	mode        http
	#URI相对地址
 	stats uri   /dbs_monitor
	#统计报告格式
 	stats realm     Global\ statistics
	#登陆帐户信息
 	stats auth  admin:admin
	#数据库负载均衡
	listen  proxy-mysql
	#访问的IP和端口，haproxy开发的端口为3306
 	#假如有人访问haproxy的3306端口，则将请求转发给下面的数据库实例
	bind  0.0.0.0:3306  
 	#网络协议
	mode  tcp
	#负载均衡算法（轮询算法）
	#轮询算法：roundrobin
	#权重算法：static-rr
	#最少连接算法：leastconn
	#请求源IP算法：source 
 	balance  roundrobin
	#日志格式
 	option  tcplog
	#在MySQL中创建一个没有权限的haproxy用户，密码为空。
	#Haproxy使用这个账户对MySQL数据库心跳检测
 	option  mysql-check user haproxy
	server  MySQL_1 172.18.0.2:3306 check weight 1 maxconn 2000  
 	server  MySQL_2 172.18.0.3:3306 check weight 1 maxconn 2000  
	server  MySQL_3 172.18.0.4:3306 check weight 1 maxconn 2000 
	#使用keepalive检测死链
 	option  tcpka
```

> (3)创建haproxy容器，因为当前centos的网络和win使用的是桥接，所以直接端口映射到centos上即可，如果不想用桥接，则需要修改Vagrantfile进行端口映射

```
#这样可以直接访问centos的IP:8888和3306
docker run -it -d -p 8888:8888 -p 3306:3306 -v /tmp/haproxy:/usr/local/etc/haproxy --name haproxy01 --privileged --net=pxc-net haproxy
```

> (4)根据haproxy.cfg文件启动haproxy

```
docker exec -it haproxy01 bash
haproxy -f /usr/local/etc/haproxy/haproxy.cfg
```

> (5)在MySQL数据库上创建用户，用于心跳检测

```
CREATE USER 'haproxy'@'%' IDENTIFIED BY '';
[小技巧[如果创建失败，可以先输入一下命令]:
    drop user 'haproxy'@'%';
    flush privileges;
    CREATE USER 'haproxy'@'%' IDENTIFIED BY '';
]
```

> (6)win浏览器访问

```
http://centos_ip:8888/dbs_monitor
用户名密码都是:admin
```

> (7)win上的datagrip连接haproxy01

```
ip:centos_ip
port:3306
user:root
password:jack123
```

> (8)在haproxy连接上进行数据操作，然后查看数据库集群各个节点











**MySQL容器准备**

> (1)创建volume

```
docker volume create v1
```

> (2)创建mysql容器

```
docker run -d --name my-mysql -v v1:/var/lib/mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=jack123 --net=pro-net --ip 172.18.0.6 mysql
```

> (3)datagrip连接，执行.mysql文件

```
name:my-mysql
ip:centos-ip
端口:3306
user:root
password:jack123
```

```sql
create schema db_pro collate utf8mb4_0900_ai_ci;
use db_pro;
create table t_user
(
	id int not null
		primary key,
	username varchar(50) not null,
	password varchar(50) not null,
	number varchar(100) not null
);
```





**Spring Boot项目准备**

> Spring Boot+MyBatis实现CRUD操作，名称为“springboot-mybatis”

```
(1)在本地测试该项目的功能
	主要是修改application.yml文件中数据库的相关配置

(2)在项目根目录下执行mvn clean package打成一个jar包
	[记得修改一下application.yml文件数据库配置]
	mvn clean package -Dmaven.test.skip=true
	在target下找到"springboot-mybatis-0.0.1-SNAPSHOT.jar.jar"
    
(3)在docker环境中新建一个目录"springboot-mybatis"
    
(4)安装文件传输工具yum install lrzsz，然后上传"springboot-mybatis-0.0.1-SNAPSHOT.jar"到该目录下，并且在此目录创建Dockerfile

(5)编写Dockerfile内容
	FROM openjdk:8
    MAINTAINER itcrazy2016
    LABEL name="springboot-mybatis" version="1.0" author="itcrazy2016"
    COPY springboot-mybatis-0.0.1-SNAPSHOT.jar springboot-mybatis.jar
    CMD ["java","-jar","springboot-mybatis.jar"]
    
(6)基于Dockerfile构建镜像
	docker build -t sbm-image .

(7)基于image创建container
	docker run -d --name sb01 -p 8081:8080 --net=pro-net --ip 172.18.0.11 sbm-image
	
(8)查看启动日志docker logs sb01
	
(9)在win浏览器访问http://192.168.8.118:8081/user/listall
```



**创建多个项目容器**

```
docker run -d --name sb01 -p 8081:8080 --net=pro-net --ip 172.18.0.11 sbm-image
docker run -d --name sb02 -p 8082:8080 --net=pro-net --ip 172.18.0.12 sbm-image
docker run -d --name sb03 -p 8083:8080 --net=pro-net --ip 172.18.0.13 sbm-image
```







**Nginx准备**

> (1)在centos的/tmp/nginx下新建nginx.conf文件，并进行相应的配置

```
user nginx;
worker_processes  1;
events {
    worker_connections  1024;
}
http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;
    sendfile        on;
    keepalive_timeout  65; 

   
    server {
        listen 80;
        location / {
         proxy_pass http://balance;
        }
    }
    
    upstream balance{  
        server 172.18.0.11:8080;
        server 172.18.0.12:8080;
        server 172.18.0.13:8080;
    }
    include /etc/nginx/conf.d/*.conf;
}
```

> (2)创建nginx容器
>
> `注意`：先在centos7上创建/tmp/nginx目录，并且创建nginx.conf文件，写上内容

```
docker run -d --name my-nginx -p 80:80 -v /tmp/nginx/nginx.conf:/etc/nginx/nginx.conf --network=pro-net --ip 172.18.0.10 nginx
```

> (3)win浏览器访问: ip[centos]/user/listall

`思考`：若将172.18.0.11/12/13改成sb01/02/03是否可以？

