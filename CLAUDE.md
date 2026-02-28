# AI头像生成网站开发文档 ## 1. 项目概述 ### 1.1 项目目标 构建一个基于Spring AI的AI头像生成平台，提供专业化的头像生成服务，具备智能缓存、多厂商调度、数据监控等核心功能。 ### 1.2 核心竞争力 - **专业模板化服务**：提供预设的高质量头像模板，降低用户使用门槛 - **社交专用优化**：针对微信、朋友圈等场景进行尺寸和样式优化 - **数据闭环**：基于用户历史偏好的个性化推荐 - **多云调度**：通过Nacos实现多厂商API的动态切换 ## 2. 技术架构 ### 2.1 整体架构图
用户端 → Nginx → Spring Boot应用 → Spring AI → 多厂商API
↑        ↑         ↑
Redis    MySQL     Nacos
### 2.2 技术栈选型 #### 后端技术栈 | 组件 | 技术选型 | 作用 | | ------ |------ |------ | | 核心框架 | Spring Boot 3.x | 基础框架 | | AI框架 | Spring AI | 统一调度AI厂商 | | 配置中心 | Nacos | 动态调度配置 | | 缓存 | Redis | 缓存图片和Token | | 数据库 | MySQL | 存储用户和订单数据 | | 权限网关 | Spring Cloud Gateway + Sa-Token | 请求路由和鉴权 | #### 前端技术栈 | 组件 | 技术选型 | 作用 | | ------ |------ |------ | | 管理后台 | Vue 3 + Element Plus | 管理系统界面 | | 用户端 | UniApp | 多端统一开发 | ### 2.3 架构模式 采用**领域驱动设计（DDD）和整洁架构**，分为： - **接口适配层**：Controller、API网关 - **应用层**：业务流程编排 - **领域层**：核心业务逻辑和实体 - **基础设施层**：数据库、缓存、第三方服务 ## 3. 核心功能设计 ### 3.1 AI生成模块 #### 3.1.1 多厂商调度策略
@Service
public class RoundRobinImageService implements ScheduledImageService {
private final List<ImageModel> imageModels;
private final AtomicInteger currentIndex = new AtomicInteger(0);

    @Override
    public String generate(String prompt) {
        int index = currentIndex.getAndIncrement();
        int targetIndex = index % imageModels.size();
        ImageModel selectedModel = imageModels.get(targetIndex);
        
        ImagePrompt promptObj = new ImagePrompt(prompt);
        ImageResponse response = selectedModel.call(promptObj);
        
        return response.getResult().getOutput().getUrl();
    }
}
#### 3.1.2 Nacos动态配置 **Nacos配置格式（ai-strategy.json）：**
[
{
"vendor": "openai",
"enabled": true,
"weight": 50,
"timeout": 10000
},
{
"vendor": "google",
"enabled": true,
"weight": 50,
"timeout": 15000
}
]
### 3.2 智能缓存模块 #### 3.2.1 双层缓存策略 1. **精确缓存**：使用MD5对提示词进行哈希 2. **语义缓存**：使用Embedding向量化用户输入 #### 3.2.2 缓存实现
@Service
public class AvatarService {
private final OpenAiImageModel openAiImageModel;
private final RedisTemplate redisTemplate;

    public String generateAvatar(String prompt) {
        String cacheKey = "avatar:" + DigestUtils.md5DigestAsHex(prompt.getBytes());
        String cachedUrl = (String) redisTemplate.opsForValue().get(cacheKey);
        
        if (cachedUrl != null) {
            return cachedUrl;
        }
        
        // 调用AI接口
        ImagePrompt imagePrompt = new ImagePrompt(prompt);
        ImageResponse response = openAiImageModel.call(imagePrompt);
        String imageUrl = response.getResult().getOutput().getUrl();
        
        redisTemplate.opsForValue().set(cacheKey, imageUrl, Duration.ofHours(1));
        return imageUrl;
    }
}
### 3.3 去水印解决方案 #### 3.3.1 组合拳策略 1. **平台隐藏逻辑**：利用豆包、即梦的前端特性获取原图 2. **Inpainting修复**：调用图像修复API去除水印 3. **数学算法**：针对Gemini水印的逆向算法 #### 3.3.2 去水印服务流程
原始图片 → 检测水印 → 选择去除策略 → 输出无水印图片
### 3.4 用户与支付模块 #### 3.4.1 定价策略 | 套餐 | 权益 | 价格 | 目标用户 | | ------ |------ |------ |------ | | 免费版 | 每日3次，低分辨率 | 0元 | 引流用户 | | 体验包 | 50次生成，7天有效 | 5.9元 | 临时需求用户 | | 月卡会员 | 每日50次，高清无水印 | 19.9元/月 | 高频活跃用户 | ## 4. 第三方API厂商 ### 4.1 推荐API组合 | 类型 | 厂商 | 特点 | 获取方式 | | ------ |------ |------ |------ | | 国外首选 | Google Gemini 3.1 | 速度快，成本低 | 一步API(yibuapi.com) | | 国外备选 | OpenAI DALL-E 3 | 效果最好 | 官方API Key | | 国内首选 | 腾讯混元 | 免费额度高 | 腾讯云官网 | | 国内备选 | 智谱AI | 中文理解好 | 官网注册 | ### 4.2 API调用策略 - **免费用户**：腾讯混元或Bing - **付费用户**：Google Gemini或OpenAI - **缓存用户**：直接读取Redis ## 5. 管理后台 ### 5.1 核心功能模块 #### 5.1.1 用户管理 - 查看所有用户信息 - 封禁恶意用户 - 查看用户使用记录 #### 5.1.2 数据监控 - 实时生成记录 - API调用成功率 - 用户活跃度统计 #### 5.1.3 财务报表 - 收入统计 - 用户充值分析 - 成本消耗分析 ### 5.2 技术实现 推荐使用开源Admin系统（如RuoYi）快速搭建管理后台。 ## 6. 部署方案 ### 6.1 服务器配置 - **应用服务器**：2核4G，部署Spring Boot应用 - **数据库服务器**：2核4G，部署MySQL和Redis - **Nacos服务器**：2核4G，部署Nacos配置中心 ### 6.2 网络架构
公网IP → Nginx → 应用服务器
↓
内网通信
↓
数据库/缓存服务器
## 7. 开发计划 ### 7.1 第一阶段（MVP） 1. 搭建Spring Boot基础框架 2. 集成Spring AI和Nacos 3. 实现基础生成功能 4. 搭建管理后台基础功能 ### 7.2 第二阶段（功能完善） 1. 实现智能缓存 2. 开发去水印功能 3. 完善用户系统 4. 集成支付功能 ### 7.3 第三阶段（商业运营） 1. 上线推广 2. 数据分析优化 3. 用户反馈收集 4. 持续迭代改进 ## 8. 风险与应对 ### 8.1 技术风险 - **API不稳定**：通过多厂商调度和缓存降低影响 - **水印问题**：采用组合拳策略确保用户体验 - **性能瓶颈**：通过Redis缓存和异步处理优化 ### 8.2 商业风险 - **大厂竞争**：专注细分场景和深度服务 - **成本控制**：通过智能缓存和动态调度降低成本 - **用户获取**：通过精准营销和口碑传播 ## 9. 总结 本项目通过Spring AI构建统一的AI调度层，结合Nacos实现动态配置管理，利用Redis实现智能缓存，通过DDD架构确保代码可维护性。项目不仅具备技术先进性，更重要的是通过专业化服务和精细化运营，构建了独特的商业竞争力。 通过分阶段实施，项目可以快速验证核心价值，逐步完善功能，最终实现商业成功。
上面的是我的开发文档，下面是我的开发优先级
🚀 开发优先级列表（最高 -> 最低）
核心原则： 先跑通“赚钱”的链路，再做“锦上添花”的功能。优先保证能生成图，再保证图好看，最后做管理。
P0：核心生死线（必须先做，否则项目无法运行）
这是你的 MVP（最小可行产品）核心，如果这些不做完，网站根本没法用。
1. 后端基础架构搭建
   ●     内容： 搭建 Spring Boot 项目，集成 Spring AI 依赖，配置 Nacos 配置中心连接。
   ●     为什么优先： 这是地基，所有代码都要往里填。
   ●     预估耗时： 1-2 天
2. AI 生成核心接口（文生图/图生图）
   ●     内容： 编写代码调用腾讯混元或 Google Gemini API，实现最基础的图片生成功能。
   ●     为什么优先： 这是你网站存在的唯一理由。先别管水印，先让图能画出来。
   ●     预估耗时： 3-4 天
3. 前端核心交互页面
   ●     内容： 一个简单的输入框（输入提示词）+ 上传按钮（上传照片）+ 生成按钮 + 图片展示区。
   ●     为什么优先： 没有这个，用户没法操作。
   ●     预估耗时： 3 天
   P1：商业闭环（做完才能收钱）
   这是你区别于免费大模型的关键，必须紧随核心功能之后。
4. 用户系统与积分/额度控制
   ●     内容： 用户注册/登录（JWT），数据库设计用户表（包含剩余生成次数字段）。
   ●     为什么优先： 没有用户体系，你就没法限制免费用户，也没法卖会员。
   ●     预估耗时： 3 天
5. “去水印”中间件与高清修复
   ●     内容： 集成 Inpainting 修复 API 或编写简单的裁剪/覆盖逻辑。
   ●     为什么优先： 用户最痛恨水印。这是你收费的底气（“我们无水印，大厂有水印”）。
   ●     预估耗时： 3-4 天
6. 支付回调接口（模拟或真实）
   ●     内容： 对接微信支付/支付宝沙箱，或者简单的“充值卡密”逻辑。
   ●     为什么优先： 为了验证商业闭环。
   ●     预估耗时： 2-3 天
   P2：运营与管理（做完才能省心）
   这是为了让你能监控系统、防止被盗刷。
7. 管理后台（Admin）
   ●     内容： 使用 RuoYi 等开源项目，展示用户列表、生成日志、充值记录。
   ●     为什么优先： 你不能靠看数据库来管理用户。
   ●     预估耗时： 3 天
8. Nacos 动态调度配置
   ●     内容： 在 Nacos 里配置当前使用哪家厂商（Gemini/腾讯云），代码监听配置变化。
   ●     为什么优先： 这是你的技术亮点，也是防封/省钱的手段。
   ●     预估耗时： 2 天
   P3：体验优化（锦上添花）
   这是为了提高用户留存率，可以后期迭代。
9. 热门模板/风格库
   ●     内容： 预设“赛博朋克”、“国潮”等一键生成按钮。
   ●     为什么优先： 帮助不会写提示词的用户生成好看的图。
   ●     预估耗时： 2 天
10. AI 自动配文/签名生成功能
    ●     内容： 调用 LLM 生成朋友圈文案。
    ●     为什么优先： 增加社交传播属性。
    ●     预估耗时： 1-2 天
    💡 建议的开发节奏（周计划）
    ●     第 1-2 周： 死磕 P0。目标是跑通“输入文字 -> 生成图片”的全流程。
    ●     第 3-4 周： 攻克 P1。目标是实现“用户登录 -> 扣次数 -> 生成无水印图”。
    ●     第 5-6 周： 完成 P2。目标是搭建好后台，能看到谁在用、用了多少次。
    ●     第 7-8 周： 完善 P3 和测试。修复 Bug，优化界面。
生成代码不要一次性全部生成，一个阶段完成后等待我的指令进入下阶段，并在每次生成代码后告诉我这次任务干了什么修改了哪些文件代码。