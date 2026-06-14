# =====================================================================
# 脚本名  : move-project-to-ascii-path.ps1
# 用途    : 把 Android 项目从含中文/空格的路径
#           迁到纯 ASCII 路径，规避 AGP 8.2.x 的非 ASCII 路径检查
#
# 源路径  : D:\文档\vib coding\QuestionBankAndroid
# 目标路径: D:\workspace\QuestionBankAndroid
# 旧目录  : 改名为 D:\文档\vib coding\QuestionBankAndroid_backup_<时间戳>
#
# 用法    :
#   1. 右键此文件 -> "使用 PowerShell 运行"
#   2. 或在 PowerShell 中执行:
#         Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
#         .\move-project-to-ascii-path.ps1
#
# 退出码  :
#   0 = 成功
#   非 0 = 失败（脚本会打印原因）
# =====================================================================

$ErrorActionPreference = "Stop"

# ---------- 配置 ----------
$SRC = "D:\文档\vib coding\QuestionBankAndroid"
$DST = "D:\workspace\QuestionBankAndroid"
$TS  = Get-Date -Format "yyyyMMdd-HHmmss"
$BACKUP = "${SRC}_backup_${TS}"

# ---------- 1) 源路径检查 ----------
Write-Host "[1/5] 检查源目录..." -ForegroundColor Cyan
if (-not (Test-Path $SRC)) {
    Write-Host "  ✗ 源目录不存在: $SRC" -ForegroundColor Red
    Write-Host "    请先在 Android Studio 中确认项目实际位置" -ForegroundColor Yellow
    exit 1
}
Write-Host "  ✓ 源目录存在" -ForegroundColor Green

# ---------- 2) 目标路径检查 ----------
Write-Host "[2/5] 检查目标目录..." -ForegroundColor Cyan
if (Test-Path $DST) {
    Write-Host "  ✗ 目标目录已存在: $DST" -ForegroundColor Red
    Write-Host "    请手动删除/重命名后再运行，或修改本脚本的 `$DST 变量" -ForegroundColor Yellow
    exit 2
}
# 确保父目录 D:\workspace 存在
$dstParent = Split-Path -Parent $DST
if (-not (Test-Path $dstParent)) {
    Write-Host "  · 创建父目录: $dstParent"
    New-Item -ItemType Directory -Path $dstParent -Force | Out-Null
}
Write-Host "  ✓ 目标路径可用: $DST" -ForegroundColor Green

# ---------- 3) 尝试停 Gradle Daemon（避免 .gradle 目录被锁） ----------
Write-Host "[3/5] 停止 Gradle Daemon..." -ForegroundColor Cyan
$gradlew = Join-Path $SRC "gradlew.bat"
if (Test-Path $gradlew) {
    try {
        # 5 秒超时，daemon 不响应就直接进入复制步骤
        $job = Start-Job -ScriptBlock {
            Set-Location $using:SRC
            & cmd /c "gradlew.bat --stop" 2>&1
        }
        if (Wait-Job $job -Timeout 5) {
            Receive-Job $job
            Remove-Job $job
            Write-Host "  ✓ Gradle Daemon 已停止" -ForegroundColor Green
        } else {
            Stop-Job $job
            Remove-Job $job -Force
            Write-Host "  ! Gradle Daemon 未在 5 秒内停止，继续尝试复制" -ForegroundColor Yellow
        }
    } catch {
        Write-Host "  ! 停止 Gradle 失败: $($_.Exception.Message)，继续尝试复制" -ForegroundColor Yellow
    }
} else {
    Write-Host "  · 未找到 gradlew.bat，跳过" -ForegroundColor Gray
}

# ---------- 4) 复制项目 ----------
Write-Host "[4/5] 复制项目到 $DST ..." -ForegroundColor Cyan
try {
    # robocopy 比 Copy-Item 更稳，对长路径、中文路径、隐藏文件更友好
    # /MIR  = 镜像复制（目标不存在时等价于 /E）
    # /R:0  = 失败不重试
    # /W:0  = 重试间隔 0 秒
    # /NFL /NDL /NP /NS /NC = 静默模式
    robocopy $SRC $DST /MIR /R:0 /W:0 /NFL /NDL /NP /NS /NC | Out-Null
    $rc = $LASTEXITCODE
    if ($rc -gt 7) {
        # robocopy 返回码：0=无变化, 1=复制成功, 2=额外文件, 3=混合, >=8=错误
        Write-Host "  ✗ robocopy 失败，返回码 $rc" -ForegroundColor Red
        exit 3
    }
    Write-Host "  ✓ 复制完成" -ForegroundColor Green
} catch {
    Write-Host "  ✗ 复制失败: $($_.Exception.Message)" -ForegroundColor Red
    exit 4
}

# ---------- 5) 旧目录改名 ----------
Write-Host "[5/5] 旧目录改名为: $BACKUP" -ForegroundColor Cyan
try {
    Rename-Item -Path $SRC -NewName (Split-Path -Leaf $BACKUP)
    Write-Host "  ✓ 旧目录已改名" -ForegroundColor Green
} catch {
    Write-Host "  ! 旧目录改名失败: $($_.Exception.Message)" -ForegroundColor Yellow
    Write-Host "    项目已成功复制到 $DST，可手动删除原目录" -ForegroundColor Yellow
}

# ---------- 收尾 ----------
Write-Host ""
Write-Host "================================================================" -ForegroundColor Green
Write-Host "迁移完成" -ForegroundColor Green
Write-Host "================================================================" -ForegroundColor Green
Write-Host "新项目路径: $DST" -ForegroundColor White
Write-Host "旧目录备份: $BACKUP" -ForegroundColor White
Write-Host ""
Write-Host "下一步：" -ForegroundColor Cyan
Write-Host "  1. 关闭 Android Studio 所有窗口"
Write-Host "  2. 重新打开 Android Studio"
Write-Host "  3. File -> Open -> 选 $DST"
Write-Host "  4. 等待 Gradle Sync 完成（首次会下载依赖，约 2-5 分钟）"
Write-Host ""
