@echo off
cd /d E:\huaxia\JSH_ERP\jshERP-boot
set JAVA_HOME=C:\Users\Administrator\.jdks\jdk8u492-b09
set PATH=%JAVA_HOME%\bin;%PATH%
java -Xms256m -Xmx1024m -Dspring.profiles.active=dev -Dfile.encoding=UTF-8 -jar target\jshERP.jar
