<#
.SYNOPSIS
  将本地改动提交并推送到 GitHub
.DESCRIPTION
  在本仓库根目录执行。自动暂存改动（tools/、.token 等已在 .gitignore 中排除），
  有改动才提交；提交后推送到当前分支。
.EXAMPLE
  .\git-push.ps1
  .\git-push.ps1 -Message "修复打印按钮"
  .\git-push.ps1 -NoPush
#>
param(
    [string]$Message = '',
    [string]$Branch  = '',
    [switch]$NoPush
)

$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [Text.Encoding]::UTF8; $OutputEncoding = [Text.Encoding]::UTF8 } catch {}

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
Push-Location $root
try {
    if (-not (Get-Command git -ErrorAction SilentlyContinue)) { throw '未找到 git，请先安装 Git for Windows' }
    $repo = (& git config --get remote.origin.url).Trim()
    if (-not $repo) { throw '当前目录不是 git 仓库，或未配置 origin 远端' }

    # 执行 git 命令：stderr 只做信息收集，不因警告中断脚本
    function Invoke-Git {
        param([string[]]$GitArgs, [switch]$Quiet)
        $prev = $ErrorActionPreference
        $ErrorActionPreference = 'Continue'
        try { $out = & git @GitArgs 2>&1 } finally { $ErrorActionPreference = $prev }
        $code = $LASTEXITCODE
        if ($code -ne 0) { throw "git $($GitArgs -join ' ') 失败:`n$($out -join "`n")" }
        if (-not $Quiet) { return $out }
    }

    $curBranch = if ($Branch) { $Branch } else { (Invoke-Git @('rev-parse','--abbrev-ref','HEAD')).Trim() }
    Write-Host "==> 当前分支: $curBranch"

    Invoke-Git @('add','-A','--','.') -Quiet
    $changes = @(Invoke-Git @('diff','--cached','--name-only')) | Where-Object { $_ -match '\S' }

    if (-not $changes) {
        Write-Host '==> 没有需要提交的改动'
        if (-not $NoPush) {
            Invoke-Git @('fetch','origin',$curBranch) -Quiet
            $ahead = (Invoke-Git @('rev-list','--count',"origin/$curBranch..HEAD")).Trim()
            if ($ahead -ne '0') {
                Invoke-Git @('push','origin',$curBranch) -Quiet
                Write-Host "==> 已推送 $ahead 个本地提交"
            } else {
                Write-Host '==> 已是最新，无需推送'
            }
        }
        return
    }

    Write-Host '==> 待提交文件:'
    $changes | ForEach-Object { Write-Host "    $_" }

    if (-not $Message) { $Message = '更新代码 ' + (Get-Date -Format 'yyyy-MM-dd HH:mm') }
    Invoke-Git @('commit','-m',$Message) -Quiet
    Write-Host "==> 已提交: $Message"

    if (-not $NoPush) {
        Invoke-Git @('push','origin',$curBranch) -Quiet
        Write-Host "==> 已推送到 $repo ($curBranch)"
    }
} finally {
    Pop-Location
}
