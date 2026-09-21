// 构建辅助脚本：在本机已有的 Gradle 8.7 + JDK17 + Android SDK 34 环境下编译项目。
// 仅为本机环境服务（路径写死在下面）。仓库的通用构建入口是 gradlew / gradlew.bat，
// 那两者需要能访问 services.gradle.org；本机访问不了时就用这个脚本。
const { spawn } = require('child_process');

const JAVA_HOME = 'C:\\Android\\jdk17\\jdk-17.0.20.1+1';
const ANDROID_HOME = 'C:\\Android\\Sdk';
const GRADLE = 'C:\\Android\\gradle\\gradle-8.7\\bin\\gradle.bat';
const CWD = 'D:\\txt2epub';

const args = process.argv.slice(2);
if (args.length === 0) args.push('assembleDebug');

const env = {
  ...process.env,
  JAVA_HOME,
  ANDROID_HOME,
  ANDROID_SDK_ROOT: ANDROID_HOME,
  GRADLE_USER_HOME: process.env.GRADLE_USER_HOME || 'C:\\Users\\SteveJobs\\.gradle',
};

console.log('[build] gradle ' + args.join(' '));
const p = spawn('cmd.exe', ['/c', GRADLE, ...args], { cwd: CWD, env, windowsVerbatimArguments: true });

p.stdout.on('data', (d) => process.stdout.write(String(d)));
p.stderr.on('data', (d) => process.stderr.write(String(d)));
p.on('close', (code) => {
  console.log('[build] exit=' + code);
  process.exit(code === 0 ? 0 : 1);
});
