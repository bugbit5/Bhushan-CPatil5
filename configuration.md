## Gitee Search 配置

Gitee Search 配置文件位于源码中的 core/src/main/resource/gitee-search.properties 文件中。
每次修改完该文件需要重新 `maven install` 重新打包，并重启服务。

### [ gitee-search.properties ]

Gitee Search Gateway configurations

http.bind = 127.0.0.1
http.port = 8080
http.log.pattern = /,/index/*,/search/*,/api/*
http.webroot = gateway/src/main/webapp
http.startup.tasks = indexer

# Git 账号配置（username 和 ssh 不能同时配置）
# git.username =
# git.password =
# git.ssh.key = ./data/ssh_key
# git.ssh.keypass =

# 持久化任务队列配置 (redis|embed|test)
queue.provider = embed
queue.types = repo,issue,code
queue.redis.host = 127.0.0.1
queue.redis.port = 6379
queue.redis.database = 1
queue.redis.key = gsearch-queue

# queue.embed.url = http://127.0.0.1:8080/queue/fetch
queue.embed.path = ./data/queue
queue.embed.batch_size = 10000
queue.test.auto_generate_task = true
queue.test.types = repo,issue,code

# 索引存储配置
storage.type = disk
storage.disk.path = ./data/lucene
storage.disk.use_compound_file = false
storage.disk.max_buffered_docs = -1
storage.disk.ram_buffer_size_mb = 16

# 仓库存储路径
storage.repositories.path = ./data/repositories
storage.repositories.max_size_in_gigabyte = 200

# 索引服务配置
# 没有任务时的间歇时间（单位：毫秒）
indexer.no_task_interval = 1000
indexer.batch_fetch_count = 10
indexer.tasks_per_thread = 2