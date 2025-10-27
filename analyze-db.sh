#!/bin/bash

# SSTable 分析工具启动脚本
# 使用方法: ./analyze-db.sh <命令> [参数...]

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="${PROJECT_ROOT}/target"

# 从pom.xml提取项目信息
get_project_info() {
    local pom_file="${PROJECT_ROOT}/pom.xml"
    
    if [ ! -f "$pom_file" ]; then
        echo "错误: 未找到pom.xml文件: $pom_file"
        exit 1
    fi
    
    # 提取artifactId和version
    ARTIFACT_ID=$(grep -o '<artifactId>[^<]*</artifactId>' "$pom_file" | head -1 | sed 's/<[^>]*>//g')
    VERSION=$(grep -o '<version>[^<]*</version>' "$pom_file" | head -1 | sed 's/<[^>]*>//g')
    
    if [ -z "$ARTIFACT_ID" ] || [ -z "$VERSION" ]; then
        echo "错误: 无法从pom.xml中提取项目信息"
        exit 1
    fi
    
    # 构建jar文件名
    JAR_NAME="${ARTIFACT_ID}-${VERSION}.jar"
    JAR_PATH="${TARGET_DIR}/${JAR_NAME}"
}

# 获取项目信息
get_project_info

# 检查jar文件是否存在
if [ ! -f "$JAR_PATH" ]; then
    echo "未找到jar文件: $JAR_PATH"
    echo "正在构建项目..."
    ./build.sh
    if [ $? -ne 0 ]; then
        echo "构建失败，请检查项目配置"
        exit 1
    fi
    
    # 重新检查jar文件
    if [ ! -f "$JAR_PATH" ]; then
        echo "错误: 构建完成后仍未找到jar文件: $JAR_PATH"
        exit 1
    fi
fi

# 设置类路径为jar文件
CLASSPATH="$JAR_PATH"

# 如果存在依赖jar，添加到类路径
if [ -d "target/lib" ]; then
    for jar in target/lib/*.jar; do
        if [ -f "$jar" ]; then
            CLASSPATH="$CLASSPATH:$jar"
        fi
    done
fi

echo "使用jar文件: $JAR_PATH"

# 运行分析工具
java -cp "$CLASSPATH" com.brianxiadong.lsmtree.tools.SSTableAnalyzerCLI "$@"