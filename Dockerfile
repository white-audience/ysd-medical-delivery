# syntax=docker/dockerfile:1
#
# 通用 Dockerfile：通过 MODULE 构建参数复用给任意一个微服务。
#   docker build --build-arg MODULE=ysd-catalog-service -t ysd/catalog .
# docker-compose.yml 会为每个服务传入不同的 MODULE。
#
# 运行时基于 OpenJDK 21（Eclipse Temurin 21 JRE）。
ARG MODULE=ysd-catalog-service
# 基础镜像可通过 build-arg 替换为镜像加速地址（Docker Hub 访问受限时）
ARG MAVEN_IMAGE=maven:3.9-eclipse-temurin-21
ARG JRE_IMAGE=eclipse-temurin:21-jre

# ---------- 构建阶段 ----------
FROM ${MAVEN_IMAGE} AS builder
WORKDIR /build
ARG MODULE

# 先复制源码再构建。Maven 本地仓库挂到 BuildKit 缓存，
# 多个服务镜像共用依赖缓存，避免重复下载。
COPY . .
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -pl "${MODULE}" -am clean package -Dmaven.test.skip=true

# ---------- 运行阶段 ----------
FROM ${JRE_IMAGE}
ARG MODULE

ENV TZ=Asia/Shanghai \
    LANG=C.UTF-8 \
    SERVER_ADDRESS=0.0.0.0 \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"

# curl 用于容器健康检查；tzdata 保证容器内时区与 MySQL 一致
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl tzdata \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY --from=builder "/build/${MODULE}/target/"*.jar /app/app.jar

# 非 root 运行
RUN useradd --system --uid 10001 --create-home appuser \
    && chown -R appuser:appuser /app
USER appuser

# 通用声明；实际监听端口由各服务 application.yml 的 server.port 决定
EXPOSE 8080

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
