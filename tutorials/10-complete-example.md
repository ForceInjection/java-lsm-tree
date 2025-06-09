# 第10章：完整示例

## 10.1 用户管理系统示例

这个完整示例展示了如何使用LSM Tree构建一个用户管理系统，包含用户注册、查询、更新和删除功能。

### 用户数据模型

```java
public class User {
    private String userId;
    private String username;
    private String email;
    private long createTime;
    private long lastLoginTime;
    private boolean active;
    
    public User(String userId, String username, String email) {
        this.userId = userId;
        this.username = username;
        this.email = email;
        this.createTime = System.currentTimeMillis();
        this.lastLoginTime = 0;
        this.active = true;
    }
    
    // 序列化为字符串存储
    public String serialize() {
        return String.join("|", userId, username, email, 
            String.valueOf(createTime), String.valueOf(lastLoginTime), String.valueOf(active));
    }
    
    // 从字符串反序列化
    public static User deserialize(String data) {
        String[] parts = data.split("\\|");
        User user = new User(parts[0], parts[1], parts[2]);
        user.createTime = Long.parseLong(parts[3]);
        user.lastLoginTime = Long.parseLong(parts[4]);
        user.active = Boolean.parseBoolean(parts[5]);
        return user;
    }
    
    // getters and setters
    public String getUserId() { return userId; }
    public String getUsername() { return username; }
    public String getEmail() { return email; }
    public long getCreateTime() { return createTime; }
    public long getLastLoginTime() { return lastLoginTime; }
    public boolean isActive() { return active; }
    
    public void setLastLoginTime(long lastLoginTime) { this.lastLoginTime = lastLoginTime; }
    public void setActive(boolean active) { this.active = active; }
}
```

### 用户管理服务

```java
public class UserService {
    private final LSMTree lsmTree;
    private final LSMTreeMetrics metrics;
    
    // 键前缀定义
    private static final String USER_PREFIX = "user:";
    private static final String USERNAME_INDEX_PREFIX = "username_idx:";
    private static final String EMAIL_INDEX_PREFIX = "email_idx:";
    
    public UserService(String dataDirectory) {
        this.lsmTree = new LSMTree(dataDirectory);
        this.metrics = new LSMTreeMetrics();
    }
    
    // 用户注册
    public boolean registerUser(User user) {
        try {
            // 检查用户是否已存在
            if (getUserById(user.getUserId()) != null) {
                System.out.println("用户ID已存在: " + user.getUserId());
                return false;
            }
            
            // 检查用户名是否已被使用
            if (getUserByUsername(user.getUsername()) != null) {
                System.out.println("用户名已被使用: " + user.getUsername());
                return false;
            }
            
            // 检查邮箱是否已被使用
            if (getUserByEmail(user.getEmail()) != null) {
                System.out.println("邮箱已被使用: " + user.getEmail());
                return false;
            }
            
            // 存储用户数据
            String userKey = USER_PREFIX + user.getUserId();
            lsmTree.put(userKey, user.serialize());
            
            // 创建索引
            lsmTree.put(USERNAME_INDEX_PREFIX + user.getUsername(), user.getUserId());
            lsmTree.put(EMAIL_INDEX_PREFIX + user.getEmail(), user.getUserId());
            
            metrics.recordWrite();
            System.out.println("用户注册成功: " + user.getUsername());
            return true;
            
        } catch (Exception e) {
            System.err.println("用户注册失败: " + e.getMessage());
            return false;
        }
    }
    
    // 根据用户ID查询用户
    public User getUserById(String userId) {
        try {
            String userKey = USER_PREFIX + userId;
            String userData = lsmTree.get(userKey);
            
            metrics.recordRead(userData != null);
            
            if (userData != null) {
                return User.deserialize(userData);
            }
            return null;
            
        } catch (Exception e) {
            System.err.println("查询用户失败: " + e.getMessage());
            return null;
        }
    }
    
    // 根据用户名查询用户
    public User getUserByUsername(String username) {
        try {
            String indexKey = USERNAME_INDEX_PREFIX + username;
            String userId = lsmTree.get(indexKey);
            
            if (userId != null) {
                return getUserById(userId);
            }
            return null;
            
        } catch (Exception e) {
            System.err.println("根据用户名查询失败: " + e.getMessage());
            return null;
        }
    }
    
    // 根据邮箱查询用户
    public User getUserByEmail(String email) {
        try {
            String indexKey = EMAIL_INDEX_PREFIX + email;
            String userId = lsmTree.get(indexKey);
            
            if (userId != null) {
                return getUserById(userId);
            }
            return null;
            
        } catch (Exception e) {
            System.err.println("根据邮箱查询失败: " + e.getMessage());
            return null;
        }
    }
    
    // 更新用户最后登录时间
    public boolean updateLastLogin(String userId) {
        try {
            User user = getUserById(userId);
            if (user == null) {
                return false;
            }
            
            user.setLastLoginTime(System.currentTimeMillis());
            String userKey = USER_PREFIX + userId;
            lsmTree.put(userKey, user.serialize());
            
            metrics.recordWrite();
            return true;
            
        } catch (Exception e) {
            System.err.println("更新登录时间失败: " + e.getMessage());
            return false;
        }
    }
    
    // 禁用用户账户
    public boolean deactivateUser(String userId) {
        try {
            User user = getUserById(userId);
            if (user == null) {
                return false;
            }
            
            user.setActive(false);
            String userKey = USER_PREFIX + userId;
            lsmTree.put(userKey, user.serialize());
            
            metrics.recordWrite();
            System.out.println("用户账户已禁用: " + userId);
            return true;
            
        } catch (Exception e) {
            System.err.println("禁用用户失败: " + e.getMessage());
            return false;
        }
    }
    
    // 删除用户
    public boolean deleteUser(String userId) {
        try {
            User user = getUserById(userId);
            if (user == null) {
                return false;
            }
            
            // 删除用户数据和索引
            String userKey = USER_PREFIX + userId;
            lsmTree.delete(userKey);
            lsmTree.delete(USERNAME_INDEX_PREFIX + user.getUsername());
            lsmTree.delete(EMAIL_INDEX_PREFIX + user.getEmail());
            
            metrics.recordWrite();
            System.out.println("用户已删除: " + userId);
            return true;
            
        } catch (Exception e) {
            System.err.println("删除用户失败: " + e.getMessage());
            return false;
        }
    }
    
    // 获取用户列表（分页）
    public List<User> getUsers(int offset, int limit) {
        try {
            String startKey = USER_PREFIX;
            String endKey = USER_PREFIX + "~"; // ~ 的ASCII值较大
            
            List<KeyValue> results = lsmTree.scan(startKey, endKey);
            List<User> users = new ArrayList<>();
            
            int count = 0;
            for (KeyValue kv : results) {
                if (count < offset) {
                    count++;
                    continue;
                }
                
                if (users.size() >= limit) {
                    break;
                }
                
                try {
                    User user = User.deserialize(kv.getValue());
                    users.add(user);
                } catch (Exception e) {
                    System.err.println("解析用户数据失败: " + kv.getKey());
                }
            }
            
            return users;
            
        } catch (Exception e) {
            System.err.println("获取用户列表失败: " + e.getMessage());
            return new ArrayList<>();
        }
    }
    
    // 统计活跃用户数
    public long countActiveUsers() {
        try {
            String startKey = USER_PREFIX;
            String endKey = USER_PREFIX + "~";
            
            List<KeyValue> results = lsmTree.scan(startKey, endKey);
            long activeCount = 0;
            
            for (KeyValue kv : results) {
                try {
                    User user = User.deserialize(kv.getValue());
                    if (user.isActive()) {
                        activeCount++;
                    }
                } catch (Exception e) {
                    System.err.println("解析用户数据失败: " + kv.getKey());
                }
            }
            
            return activeCount;
            
        } catch (Exception e) {
            System.err.println("统计活跃用户失败: " + e.getMessage());
            return -1;
        }
    }
    
    // 获取性能指标
    public Map<String, Long> getMetrics() {
        return metrics.getMetrics();
    }
    
    // 关闭服务
    public void close() {
        lsmTree.close();
    }
}
```

## 10.2 性能测试工具

### 基准测试类

```java
public class UserServiceBenchmark {
    private final UserService userService;
    private final Random random = new Random();
    
    public UserServiceBenchmark(String dataDirectory) {
        this.userService = new UserService(dataDirectory);
    }
    
    // 生成测试用户
    private User generateRandomUser() {
        String userId = "user_" + random.nextInt(1000000);
        String username = "user" + random.nextInt(1000000);
        String email = username + "@example.com";
        return new User(userId, username, email);
    }
    
    // 写入性能测试
    public void benchmarkWrites(int userCount) {
        System.out.println("\n=== 写入性能测试 ===");
        System.out.println("测试用户数量: " + userCount);
        
        long startTime = System.currentTimeMillis();
        int successCount = 0;
        
        for (int i = 0; i < userCount; i++) {
            User user = generateRandomUser();
            if (userService.registerUser(user)) {
                successCount++;
            }
            
            if ((i + 1) % 1000 == 0) {
                System.out.printf("已处理: %d/%d 用户\n", i + 1, userCount);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("写入完成: %d/%d 成功\n", successCount, userCount);
        System.out.printf("总耗时: %d ms\n", duration);
        System.out.printf("平均写入速度: %.2f ops/sec\n", 
            (double) successCount * 1000 / duration);
    }
    
    // 读取性能测试
    public void benchmarkReads(int readCount) {
        System.out.println("\n=== 读取性能测试 ===");
        System.out.println("测试读取次数: " + readCount);
        
        // 先获取一些用户ID用于测试
        List<User> users = userService.getUsers(0, Math.min(1000, readCount));
        if (users.isEmpty()) {
            System.out.println("没有用户数据，跳过读取测试");
            return;
        }
        
        long startTime = System.currentTimeMillis();
        int hitCount = 0;
        
        for (int i = 0; i < readCount; i++) {
            // 随机选择一个用户ID进行查询
            User randomUser = users.get(random.nextInt(users.size()));
            User result = userService.getUserById(randomUser.getUserId());
            
            if (result != null) {
                hitCount++;
            }
            
            if ((i + 1) % 1000 == 0) {
                System.out.printf("已读取: %d/%d 次\n", i + 1, readCount);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("读取完成: %d/%d 命中\n", hitCount, readCount);
        System.out.printf("总耗时: %d ms\n", duration);
        System.out.printf("平均读取速度: %.2f ops/sec\n", 
            (double) readCount * 1000 / duration);
        System.out.printf("命中率: %.2f%%\n", (double) hitCount * 100 / readCount);
    }
    
    // 混合负载测试
    public void benchmarkMixedWorkload(int totalOps, double writeRatio) {
        System.out.println("\n=== 混合负载测试 ===");
        System.out.printf("总操作数: %d, 写入比例: %.1f%%\n", 
            totalOps, writeRatio * 100);
        
        // 准备读取测试数据
        List<User> users = userService.getUsers(0, 1000);
        
        long startTime = System.currentTimeMillis();
        int writeCount = 0;
        int readCount = 0;
        
        for (int i = 0; i < totalOps; i++) {
            if (random.nextDouble() < writeRatio) {
                // 执行写入操作
                User user = generateRandomUser();
                userService.registerUser(user);
                writeCount++;
            } else if (!users.isEmpty()) {
                // 执行读取操作
                User randomUser = users.get(random.nextInt(users.size()));
                userService.getUserById(randomUser.getUserId());
                readCount++;
            }
            
            if ((i + 1) % 1000 == 0) {
                System.out.printf("已处理: %d/%d 操作\n", i + 1, totalOps);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("混合负载完成: %d 写入, %d 读取\n", writeCount, readCount);
        System.out.printf("总耗时: %d ms\n", duration);
        System.out.printf("平均操作速度: %.2f ops/sec\n", 
            (double) totalOps * 1000 / duration);
    }
    
    // 范围查询测试
    public void benchmarkRangeQueries(int queryCount, int pageSize) {
        System.out.println("\n=== 范围查询测试 ===");
        System.out.printf("查询次数: %d, 页大小: %d\n", queryCount, pageSize);
        
        long startTime = System.currentTimeMillis();
        int totalResults = 0;
        
        for (int i = 0; i < queryCount; i++) {
            int offset = random.nextInt(10000);
            List<User> results = userService.getUsers(offset, pageSize);
            totalResults += results.size();
            
            if ((i + 1) % 100 == 0) {
                System.out.printf("已查询: %d/%d 次\n", i + 1, queryCount);
            }
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.printf("范围查询完成，平均每次返回: %.1f 结果\n", 
            (double) totalResults / queryCount);
        System.out.printf("总耗时: %d ms\n", duration);
        System.out.printf("平均查询速度: %.2f ops/sec\n", 
            (double) queryCount * 1000 / duration);
    }
    
    public void close() {
        userService.close();
    }
}
```

## 10.3 完整应用示例

### 主应用程序

```java
public class UserManagementApp {
    private final UserService userService;
    private final Scanner scanner;
    
    public UserManagementApp(String dataDirectory) {
        this.userService = new UserService(dataDirectory);
        this.scanner = new Scanner(System.in);
    }
    
    public void run() {
        System.out.println("=== LSM Tree 用户管理系统 ===");
        
        while (true) {
            printMenu();
            String choice = scanner.nextLine().trim();
            
            try {
                switch (choice) {
                    case "1":
                        registerUser();
                        break;
                    case "2":
                        queryUser();
                        break;
                    case "3":
                        updateLoginTime();
                        break;
                    case "4":
                        deactivateUser();
                        break;
                    case "5":
                        deleteUser();
                        break;
                    case "6":
                        listUsers();
                        break;
                    case "7":
                        showStatistics();
                        break;
                    case "8":
                        runBenchmark();
                        break;
                    case "9":
                        System.out.println("正在关闭系统...");
                        userService.close();
                        return;
                    default:
                        System.out.println("无效选择，请重试");
                }
            } catch (Exception e) {
                System.err.println("操作失败: " + e.getMessage());
            }
            
            System.out.println();
        }
    }
    
    private void printMenu() {
        System.out.println("\n请选择操作:");
        System.out.println("1. 注册用户");
        System.out.println("2. 查询用户");
        System.out.println("3. 更新登录时间");
        System.out.println("4. 禁用用户");
        System.out.println("5. 删除用户");
        System.out.println("6. 用户列表");
        System.out.println("7. 系统统计");
        System.out.println("8. 性能测试");
        System.out.println("9. 退出");
        System.out.print("选择: ");
    }
    
    private void registerUser() {
        System.out.print("用户ID: ");
        String userId = scanner.nextLine().trim();
        
        System.out.print("用户名: ");
        String username = scanner.nextLine().trim();
        
        System.out.print("邮箱: ");
        String email = scanner.nextLine().trim();
        
        User user = new User(userId, username, email);
        boolean success = userService.registerUser(user);
        
        if (success) {
            System.out.println("用户注册成功！");
        } else {
            System.out.println("用户注册失败！");
        }
    }
    
    private void queryUser() {
        System.out.println("查询方式:");
        System.out.println("1. 根据用户ID");
        System.out.println("2. 根据用户名");
        System.out.println("3. 根据邮箱");
        System.out.print("选择: ");
        
        String choice = scanner.nextLine().trim();
        User user = null;
        
        switch (choice) {
            case "1":
                System.out.print("用户ID: ");
                user = userService.getUserById(scanner.nextLine().trim());
                break;
            case "2":
                System.out.print("用户名: ");
                user = userService.getUserByUsername(scanner.nextLine().trim());
                break;
            case "3":
                System.out.print("邮箱: ");
                user = userService.getUserByEmail(scanner.nextLine().trim());
                break;
            default:
                System.out.println("无效选择");
                return;
        }
        
        if (user != null) {
            printUserInfo(user);
        } else {
            System.out.println("用户不存在");
        }
    }
    
    private void updateLoginTime() {
        System.out.print("用户ID: ");
        String userId = scanner.nextLine().trim();
        
        boolean success = userService.updateLastLogin(userId);
        if (success) {
            System.out.println("登录时间更新成功！");
        } else {
            System.out.println("更新失败，用户不存在");
        }
    }
    
    private void deactivateUser() {
        System.out.print("用户ID: ");
        String userId = scanner.nextLine().trim();
        
        boolean success = userService.deactivateUser(userId);
        if (success) {
            System.out.println("用户已禁用！");
        } else {
            System.out.println("禁用失败，用户不存在");
        }
    }
    
    private void deleteUser() {
        System.out.print("用户ID: ");
        String userId = scanner.nextLine().trim();
        
        User user = userService.getUserById(userId);
        if (user == null) {
            System.out.println("用户不存在");
            return;
        }
        
        System.out.printf("确定要删除用户 %s (%s) 吗？(y/N): ", 
            user.getUsername(), user.getUserId());
        String confirm = scanner.nextLine().trim();
        
        if ("y".equalsIgnoreCase(confirm) || "yes".equalsIgnoreCase(confirm)) {
            boolean success = userService.deleteUser(userId);
            if (success) {
                System.out.println("用户已删除！");
            } else {
                System.out.println("删除失败");
            }
        } else {
            System.out.println("取消删除");
        }
    }
    
    private void listUsers() {
        System.out.print("页大小 (默认10): ");
        String pageSizeStr = scanner.nextLine().trim();
        int pageSize = pageSizeStr.isEmpty() ? 10 : Integer.parseInt(pageSizeStr);
        
        System.out.print("页码 (从0开始，默认0): ");
        String pageStr = scanner.nextLine().trim();
        int page = pageStr.isEmpty() ? 0 : Integer.parseInt(pageStr);
        
        int offset = page * pageSize;
        List<User> users = userService.getUsers(offset, pageSize);
        
        if (users.isEmpty()) {
            System.out.println("没有找到用户");
        } else {
            System.out.printf("\n第%d页，共%d个用户:\n", page + 1, users.size());
            System.out.println("---------------------------------------");
            for (User user : users) {
                printUserInfo(user);
                System.out.println("---------------------------------------");
            }
        }
    }
    
    private void showStatistics() {
        System.out.println("\n=== 系统统计 ===");
        
        long activeUsers = userService.countActiveUsers();
        System.out.println("活跃用户数: " + activeUsers);
        
        Map<String, Long> metrics = userService.getMetrics();
        System.out.println("读取次数: " + metrics.get("reads"));
        System.out.println("写入次数: " + metrics.get("writes"));
        System.out.println("MemTable命中: " + metrics.get("memtable_hits"));
        System.out.println("SSTable命中: " + metrics.get("sstable_hits"));
        System.out.println("命中率: " + metrics.get("hit_rate") + "%");
        System.out.println("Compaction次数: " + metrics.get("compactions"));
    }
    
    private void runBenchmark() {
        System.out.println("\n=== 性能测试 ===");
        System.out.print("测试数据目录 (默认/tmp/benchmark): ");
        String benchmarkDir = scanner.nextLine().trim();
        if (benchmarkDir.isEmpty()) {
            benchmarkDir = "/tmp/benchmark";
        }
        
        UserServiceBenchmark benchmark = new UserServiceBenchmark(benchmarkDir);
        
        try {
            // 写入测试
            benchmark.benchmarkWrites(5000);
            
            // 读取测试
            benchmark.benchmarkReads(10000);
            
            // 混合负载测试
            benchmark.benchmarkMixedWorkload(10000, 0.3);
            
            // 范围查询测试
            benchmark.benchmarkRangeQueries(1000, 50);
            
        } finally {
            benchmark.close();
        }
    }
    
    private void printUserInfo(User user) {
        System.out.println("用户ID: " + user.getUserId());
        System.out.println("用户名: " + user.getUsername());
        System.out.println("邮箱: " + user.getEmail());
        System.out.println("创建时间: " + new Date(user.getCreateTime()));
        System.out.println("最后登录: " + 
            (user.getLastLoginTime() > 0 ? new Date(user.getLastLoginTime()) : "从未登录"));
        System.out.println("状态: " + (user.isActive() ? "活跃" : "禁用"));
    }
    
    public static void main(String[] args) {
        String dataDirectory = args.length > 0 ? args[0] : "/tmp/user_management";
        
        UserManagementApp app = new UserManagementApp(dataDirectory);
        
        // 添加关闭钩子
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n正在安全关闭系统...");
            app.userService.close();
        }));
        
        app.run();
    }
}
```

## 10.4 部署脚本

### 启动脚本 (start.sh)

```bash
#!/bin/bash

# LSM Tree 用户管理系统启动脚本

# 设置Java选项
JAVA_OPTS="-Xmx1g -Xms512m -XX:+UseG1GC"

# 数据目录
DATA_DIR=${1:-"/tmp/user_management"}

# 创建数据目录
mkdir -p $DATA_DIR

# 启动应用
echo "启动LSM Tree用户管理系统..."
echo "数据目录: $DATA_DIR"

java $JAVA_OPTS -cp target/classes:target/lib/* \
    com.example.lsmtree.UserManagementApp $DATA_DIR
```

### 配置文件 (config.properties)

```properties
# LSM Tree配置
memtable.threshold=1000
max.levels=7
bloom.filter.fpr=0.01
compression.type=none
compaction.threads=2

# WAL配置
wal.enabled=true
wal.sync.interval=1000

# 性能配置
cache.size=100MB
buffer.size=64KB

# 监控配置
metrics.enabled=true
metrics.interval=60000
```

## 10.5 运行示例

### 基本使用流程

```java
public class QuickStartExample {
    public static void main(String[] args) {
        // 1. 创建用户服务
        UserService userService = new UserService("/tmp/demo");
        
        try {
            // 2. 注册用户
            User alice = new User("001", "alice", "alice@example.com");
            User bob = new User("002", "bob", "bob@example.com");
            User charlie = new User("003", "charlie", "charlie@example.com");
            
            userService.registerUser(alice);
            userService.registerUser(bob);
            userService.registerUser(charlie);
            
            // 3. 查询用户
            User foundUser = userService.getUserByUsername("alice");
            System.out.println("找到用户: " + foundUser.getEmail());
            
            // 4. 更新登录时间
            userService.updateLastLogin("001");
            
            // 5. 获取用户列表
            List<User> users = userService.getUsers(0, 10);
            System.out.println("总用户数: " + users.size());
            
            // 6. 统计信息
            long activeUsers = userService.countActiveUsers();
            System.out.println("活跃用户: " + activeUsers);
            
            // 7. 性能指标
            Map<String, Long> metrics = userService.getMetrics();
            System.out.println("性能指标: " + metrics);
            
        } finally {
            // 8. 关闭服务
            userService.close();
        }
    }
}
```

## 10.6 性能优化建议

### 内存使用优化

1. **MemTable大小调优**
   - 根据可用内存调整`memTableThreshold`
   - 平衡内存使用和flush频率

2. **布隆过滤器优化**
   - 根据数据量调整误判率
   - 合理设置布隆过滤器容量

3. **缓存策略**
   - 实现LRU缓存减少磁盘访问
   - 缓存热点数据和索引

### 磁盘I/O优化

1. **批量写入**
   - 使用WAL批量提交
   - 合并小的写入操作

2. **Compaction优化**
   - 选择合适的compaction策略
   - 控制compaction触发条件

3. **数据压缩**
   - 启用数据压缩减少存储空间
   - 选择合适的压缩算法

这个完整示例展示了LSM Tree在实际应用中的使用方法，包含了数据模型设计、服务层实现、性能测试和用户交互界面。通过这个示例，你可以了解如何构建一个基于LSM Tree的完整应用系统。 