## 免费课程

### 设计模式

MVC外加领域模型

### 分层设计

业务领域建模分层 不同的模型解决不同的问题

<img src="second-kill.assets/image-20220505222156102.png" alt="image-20220505222156102" style="zoom:80%;" />

- 数据层：

  会参考效率设计、边界设计、分库分表设计等等

- 业务层：

  **领域模型**，拥有一个完整的生命周期，即拥有一个领域层模型对象的更新、删除到消亡的过程

  可以和Data Object做一个组合的关联关系。如用户对象，作为一个领域模型来说包含用户的基本信息和密码信息。但是在数据库层面，设计成一个用户基础表和一个用户密码表两张表的结构。那么一个业务层的Domain Model就对应两个Data Object的数据模型。因为用户密码可能会存储在一个加密数据库或其他系统当中；然后除了一些登录操作，其余的一些操作时不需要密码的，比如用户信息的展示，所以在真正调用一个UserModel的时候可以减少数据库的查询，减少存储空间

  > 数据库表结构更多的是用来关注存储层面，以及查询效率
  >
  > 而真正的领域模型才是真正需要关注**业务相关**的字段的展示

  

  **贫血**模型：在面向领域驱动开发的过程中，一个Domain Model可以理解为javaweb当中的一个bean，仅仅只有自己的一些属性，对应的set、get方法。除此之外，不提供注册，登录，注销的一些服务。必须通过service的服务来调用

- 接入层模型：

  视图模型，关注前端界面的展示，通过不同的界面聚合不同的领域模型



<img src="second-kill.assets/image-20220505224325070.png" alt="image-20220505224325070" style="zoom:80%;" />

### 包结构

数据表：**item_stock** 对于商品的库存单独来设计，因为库存的操作是非常耗时的，比如交易时库存的减操作，如果直接设计在item中的话，每次减库存都会对item的记录做一个行锁操作。单独设计一张表后，虽然也会加一个行锁，但之后如果对商品的库存有一些大的变动时，可以将这种表拆到另一个数据库当中来做优化，也可以对item_stock做分库分表。

<img src="second-kill.assets/image-20220505225730208.png" alt="image-20220505225730208" style="zoom:80%;" />

### 源码走读

### 总结

- 前后端分离的具体体系

  HTML独立部署然后用Ajax交互就是前后端分离

- 跨域问题（视频2-8）

  采取的是前后端分离的架构，通过Ajax的请求去访问。jQuery会有一个跨域的限制，即Ajax请求对应的host必须是后端服务获取到HTML页面的host，但是由于前后端分离，静态资源文件和动态请求是分开部署的，有一个跨域问题。所以**服务端**controller加上`@CrossOrigin(allowCredentials = "true", allowedHeaders = "*")`支持跨域请求；**客户端**因为session的共享也需要支持跨域，session的共享是通过cookie的方式，也需要加上`xhrFields:{withCredentials:true}`。即便加上这些参数，有时因为浏览器也会出现问题。

- 全局异常处理器404,405问题

  basecontroller因为是controller的基类 无法处理进不了controller的异常 比如404找不到处理的controller 因此需要用exceptionhandler。定义一个**GlobalExceptionHandler**

  - 404：在配置文件中加两个配置

    静态资源的配置定义spring.mvc.throw-exception-if-no-handler-found
    =true,由于springboot的mvc机制默认对于找不到handler，也就是找不到路径处理controller的方法时会使用404的错误交给servlet默认去处理，因此需要设置成true使其可以抛出异常，这样才能被我们定义的全局异常处理捕获到。其次 spring.resources.add-mappings表示我们不要开启默认的静态资源处理机制，而使用我们自己定义的静态资源处理的resourcesHandler。因此需要手动添加静态资源处理的handler

  - 405：绑定路径问题（参数不对问题）



## 云端部署

服务器（centos）

用户：root

密码：wxhtobe1..

### java 环境安装

### MySQL安装

安装

- `yum install mysql*`
- `yum install mariadb-server`
- `systemctl start mariadb.service`

> yum安装会向maven一样自动把依赖安装好，比rpm更加高效



设置密码

- `mysqladmin -u root password wxhtobe1`
- 登录：`mysql -uroot -pwxhtobe1`



### 数据库部署

#### 备份

<img src="second-kill.assets/image-20220506210248778.png" alt="image-20220506210248778" style="zoom:80%;" />

#### 服务器导入

- 上传备份文件
- 导入：`mysql -uroot -pwxhtobe1 < /tmp/seckill.sql`

### 打包上传

这里和视频中不同，需要指定版本，添加了：

```java
<plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.8.1</version>
        <configuration>
          <source>1.8</source>
          <target>1.8</target>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
        <version>2.4.3</version>
      </plugin>
    </plugins>
```

> 貌似spring-boot-maven-plugin只能放到最后，同maven-compiler-plugin放到一起，才会成功打成jar包（看问答里说的，不一定）

- maven打包（前后端分离，生成的target目录里的jar并没有HTML文件）

  `mvn clean package `（在项目根目录）

  war包需要tomcat服务器启动， **jar包**springboot内嵌的tomcat 比较方便 一体化

- 上传（我是通过xftp直接上传的）

  <img src="second-kill.assets/image-20220506215533000.png" alt="image-20220506215533000" style="zoom:80%;" />

- 测试：✓
  - 需要在阿里云服务器中打开8888安全组（视频中是8090）

### deploy脚本启动

服务端部署时往往需要修改额外的配置，如端口、数据库配置

做法：

- ~~部署时修改application配置文件~~ 

- 需要一个外挂配置文件的能力

  上传后架包里的配置文件保持不变，然后在服务器部署的工程目录下有一个自己的`application.yml`文件。启动架包时，首先读取工程内部配置文件，再读取外挂配置文件，并且外挂配置文件优先级更高。

  然后在启动时指定一个额外配置文件的路径

  <img src="second-kill.assets/image-20220506224338726.png" alt="image-20220506224338726" style="zoom:80%;" />

但是这样也必比较笨拙，应用程序必须启动在界面上

- 编写deploy脚本启动

  ```sh
  nohup java -Xms400m -Xmx400m -XX:NewSize=200m -XX:MaxNewSize=200m -jar miaosha.jar --spring.config.addition-location=/var/www/miaosha/application.yml
  ```

  <img src="second-kill.assets/image-20220506231607264.png" alt="image-20220506231607264" style="zoom:80%;" />

  上面这个启动命令应该是：`./deploy.sh &`（图片中少写了个&）

  

  nohup：命令行中启动应用程序，启动应用程序的这个界面退出，应用程序也不会退出。关闭deploy启动的程序可以通过：直接杀掉进程（`kill 5240`)

  > JVM的参数分别指的是初始jvm堆栈大小 最大jvm堆栈大小 初始jvm新生代大小 最大新生代大小
  >
  > 是根据 **业务的不同能力和压测及线上环境的不断验证** 来设置



### jmeter性能压测

<img src="second-kill.assets/image-20220506233131377.png" alt="image-20220506233131377" style="zoom:50%;" />

#### 启动并添加上面的四部分

进入安装目录的bin目录

- 双击`jmeter.bat`（双击bin目录下的ApacheJMeter.jar启动中文版本）
- 或者执行命令`java -jar Apache JMeter.jar`

#### 配置映射地址

- `sudo vim //etc/hosts`

  每一台机器上都有一个host文件，做DNS解析时优先会查询里面对应的域名。客户端发起一次查询，默认会先从host文件里查看对应的一个映射，如果存在，优先使用这个域名对应的映射

- 里面加上：`39.107.152.73 seckillserver`

- 然后`ping seckillserver`，返回的就是这个地址

> 本地的hosts文件也要修改（C:\Windows\System32\drivers\etc）

#### Jmeter压测发现并发容量问题

- 参数配置（线程数等）

- 当设置5000个线程时，发现已经**出现错误**，服务可能拒绝连接，并且服务器线程数也已经达到220，通过top -H 可以看到**MySQL的CPU占用率特别高**，因为这个测试中的请求是获取商品信息，获取压力基本都在数据库

  <img src="second-kill.assets/image-20220507195920537.png" alt="image-20220507195920537" style="zoom: 80%;" />

  <img src="second-kill.assets/image-20220507195844580.png" alt="image-20220507195844580" style="zoom: 80%;" />

> 线程数对系统不是越多越好，是一个由上升到下降的状态，线程越多对cpu消耗越大，反而发挥不出效果，扩大线程池是为了达到最佳状态，限制线程数是为了寻找最好的效果点

- 出现问题的原因是server端并发线程数上不去

##### 解法方法

1. 查看`spring-configuration-metadata.json`文件，查看各个节点的配置

   <img src="second-kill.assets/image-20220507202046687.png" alt="image-20220507202046687" style="zoom:80%;" />

   因此服务端在上线前一定要将容器的配置，线程池的配置，连接数的配置都检查一遍，以保证在生产环境下是最优的

2. 修改服务器对应的`application.yml`文件（注意和视频中文件格式不一样）

   ```yaml
   server:
     port: 80
     tomcat:
       accept-count: 1000
       max-connections: 10000
       threads:
         max: 800
         min-spare: 100
   ```

   > 上面这些值的设计没有固定的公式， 一般都是要根据不同的硬件能力，在不断的实际环境测试中寻找最佳值
   >
   > 例如：
   >
   > 1 vCPU 2 GiB的服务器最大线程数设计为100就最多了
   >
   > 然后等待队列不要超过100

3. **在代码没有优化前，进行一个初步的参数调优（Tomact系统参数）之后，并发的能力已经增加**。`pstree -p 18709 | wc -l`开启应用前查看线程数已经增加。测试开始后，线程数也比之前多。

   我的阿里云服务器配置可能较高，线程增加没有视频中明显。 

   

   但由于java应用和MySQL部署在同一台服务器上，其实整台机器的负载上还是很高，jmete中显示的tps查询量还是不高 ，有待优化。

> jmeter由于线程设置大可能会卡死，修改一下bin目录下的jmeter.bat启动文件中的配置：
>
> `HEAP=-Xms1024m -Xmx1024m`（原来是：-Xms1g -Xmx1g -XX:MaxMetaspaceSize=256m）

### 定制化内嵌tomcat开发

tomcat的一些其他需要用到的参数在idea中并没有提供配置文件修改的支持。比如**KeepAlive**（代表客服端和服务端请求后，不要立马断开链接，而是等待复用链接。jmeter也要勾选）。

需要定制化内嵌tomcat开发：

- keepAliveTimeOut：多少毫秒后不响应的断开keepalive
- maxKeepAliveRequests：多少次请求后keepalive断开失效

**使用WebServerFactoryCustomizer\<ConfigurableServeletWebServerFactory>定制化内嵌tomcat配置**

> 当springboot启动后，我们定义了一个webserverconfiguration，服务器中的application.yml文件中的参数会加载到里面的内里面的ConfigurableWebServerFactory内 然后通过里面的customize可以再次修改参数



### 总结

- `netstat -anp | grep 5240`：通过端口查看进程

- `ps -ef | grep java`：查看进程

- `pstree -p 5240`：查看进程对应线程

- `pstree -p 5240 | wc -l`：查看进程对应线程数量

- `top -H`：查看服务器性能

  里面的几个指标：

  - us：用户态下的CPU占有率，
  - st：内核空间的CPU占用率
  - load average：最近的一分钟，5分钟，15分钟对应的CPU的load的数量，越低越好。例如2核的CPU就控制在2以内就好
  - 下面是pid列表