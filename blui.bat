@echo off
chcp 65001 > nul
if exist blui.jar (
    java -jar blui.jar %*
) else (
    java -cp bin blui.app.BluiCli %*
)

