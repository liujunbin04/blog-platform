# 官方 Elasticsearch 镜像不含 IK 中文分词插件。
# ArticleDocument 的 title/content 字段用了 ik_max_word（建索引）和 ik_smart（搜索），
# 缺插件时 Spring Data Elasticsearch 自动建索引会直接失败：
#   mapper_parsing_exception: analyzer [ik_smart] has not been configured in mappings
# 后端随即启动不起来。
#
# 插件版本必须与 ES 版本完全一致，否则插件加载会被拒绝。
FROM docker.elastic.co/elasticsearch/elasticsearch:8.17.4

RUN bin/elasticsearch-plugin install --batch \
    https://get.infini.cloud/elasticsearch/analysis-ik/8.17.4
