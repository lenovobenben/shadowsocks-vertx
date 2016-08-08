/*
 *   Copyright 2016 Author:NU11 bestoapache@gmail.com
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package shadowsocks.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import gnu.getopt.Getopt;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

import io.vertx.core.json.JsonObject;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

public class GlobalConfig{

    public static Logger log = LogManager.getLogger(GlobalConfig.class.getName());

    private static GlobalConfig mConfig;

    private ReentrantLock mLock = new ReentrantLock();

    private AtomicReference<String> mPassword;
    private AtomicReference<String> mMethod;
    private AtomicReference<String> mServer;
    private AtomicReference<String> mConfigFile;
    private AtomicInteger mPort;
    private AtomicInteger mLocalPort;
    private AtomicInteger mTimeout; /* UNIT second */
    private AtomicBoolean mOneTimeAuth;
    private AtomicBoolean mIsServerMode;

    final private static String DEFAULT_METHOD = "aes-256-cfb";
    final private static String DEFAULT_PASSWORD = "123456";
    final private static String DEFAULT_SERVER = "127.0.0.1";
    final private static int DEFAULT_PORT = 8388;
    final private static int DEFAULT_LOCAL_PORT = 9999;
    final private static int DEFAULT_TIMEOUT = 300;

    //Lock
    public void getLock() {
        mLock.lock();
    }
    public void releaseLock() {
        mLock.unlock();
    }

    //Timeout
    public void setTimeout(int t) {
        mTimeout.set(t);
    }
    public int getTimeout() {
        return mTimeout.get();
    }

    //Password(Key)
    public void setPassowrd(String p) {
        mPassword.set(p);
    }
    public String getPassword() {
        return mPassword.get();
    }

    //Method
    public void setMethod(String m) {
        mMethod.set(m);
    }
    public String getMethod() {
        return mMethod.get();
    }

    //Server
    public void setServer(String s) {
        mServer.set(s);
    }
    public String getServer() {
        return mServer.get();
    }

    //Server port
    public void setPort(int p) {
        mPort.set(p);
    }
    public int getPort() {
        return mPort.get();
    }

    //Local port
    public void setLocalPort(int p) {
        mLocalPort.set(p);
    }
    public int getLocalPort() {
        return mLocalPort.get();
    }

    //One time auth
    public void setOTAEnabled(boolean enable){
        mOneTimeAuth.set(enable);
    }
    public boolean isOTAEnabled()
    {
        return mOneTimeAuth.get();
    }

    //Running in server/local mode
    private void setServerMode(boolean isServer){
        mIsServerMode.set(isServer);
    }
    public boolean isServerMode(){
        return mIsServerMode.get();
    }

    //Config
    public void setConfigFile(String name){
        mConfigFile.set(name);
    }
    public String getConfigFile(){
        return mConfigFile.get();
    }

    public synchronized static GlobalConfig get()
    {
        if (mConfig == null)
        {
            mConfig = new GlobalConfig();
        }
        return mConfig;
    }

    public GlobalConfig()
    {
        mMethod = new AtomicReference<String>(DEFAULT_METHOD);
        mPassword = new AtomicReference<String>(DEFAULT_PASSWORD);
        mServer = new AtomicReference<String>(DEFAULT_SERVER);
        mPort = new AtomicInteger(DEFAULT_PORT);
        mLocalPort = new AtomicInteger(DEFAULT_LOCAL_PORT);
        mOneTimeAuth = new AtomicBoolean(false);
        mIsServerMode = new AtomicBoolean(false);
        mConfigFile = new AtomicReference<String>();
        mTimeout = new AtomicInteger(DEFAULT_TIMEOUT);
    }

    public void printConfig(){
        log.info("Current config is:");
        log.info("Mode [" + (isServerMode()?"Server":"Local") + "]");
        log.info("Crypto method [" + getMethod() + "]");
        log.info("Password [" + getPassword() + "]");
        log.info("Auth [" + isOTAEnabled() + "]");
        if (isServerMode()) {
            log.info("Bind port [" + getPort() + "]");
        }else{
            log.info("Server [" + getServer() + "]");
            log.info("Server port [" + getPort() + "]");
            log.info("Local port [" + getLocalPort() + "]");
        }
        log.info("Timeout [" + getTimeout() + "]");
    }

    public static String readConfigFile(String name){
        try{
            BufferedReader reader = new BufferedReader(new FileReader(name));
            char [] data = new char[4096]; /*4096*/
            int size = reader.read(data, 0, data.length);
            if (size < 0)
                return null;
            return new String(data);
        }catch(IOException e){
            log.error("Read config file " + name + " error.", e);
            return null;
        }
    }

    public static void getConfigFromFile() throws ClassCastException{
        String name = GlobalConfig.get().getConfigFile();
        if (name == null)
            return;
        String data = GlobalConfig.readConfigFile(name);

        JsonObject jsonobj = new JsonObject(data);

        if (jsonobj.containsKey("server")) {
            String server = jsonobj.getString("server");
            log.debug("CFG:Server address: " + server);
            GlobalConfig.get().setServer(server);
        }
        if (jsonobj.containsKey("server_port")) {
            int port = jsonobj.getInteger("server_port").intValue();
            log.debug("CFG:Server port: " + port);
            GlobalConfig.get().setPort(port);
        }
        if (jsonobj.containsKey("local_port")) {
            int lport = jsonobj.getInteger("local_port").intValue();
            log.debug("CFG:Local port: " + lport);
            GlobalConfig.get().setLocalPort(lport);
        }
        if (jsonobj.containsKey("password")) {
            String password = jsonobj.getString("password");
            log.debug("CFG:Password: " + password);
            GlobalConfig.get().setPassowrd(password);
        }
        if (jsonobj.containsKey("method")) {
            String method = jsonobj.getString("method");
            log.debug("CFG:Crypto method: " + method);
            GlobalConfig.get().setMethod(method);
        }
        if (jsonobj.containsKey("auth")) {
            boolean auth = jsonobj.getBoolean("auth").booleanValue();
            log.debug("CFG:One time auth: " + auth);
            GlobalConfig.get().setOTAEnabled(auth);
        }
        if (jsonobj.containsKey("timeout")) {
            int timeout = jsonobj.getInteger("timeout").intValue();
            log.debug("CFG:Timeout: " + timeout);
            GlobalConfig.get().setTimeout(timeout);
        }
        if (jsonobj.containsKey("server_mode")) {
            boolean isServer = jsonobj.getBoolean("server_mode").booleanValue();
            log.debug("CFG:Running on server mode: " + isServer);
            GlobalConfig.get().setServerMode(isServer);
        }
    }
    public static boolean getConfigFromArgv(String argv[])
    {

        Getopt g = new Getopt("shadowsocks", argv, "SLm:k:p:as:l:c:t:h");
        int c;
        String arg;
        while ((c = g.getopt()) != -1)
        {
            switch(c)
            {
                case 'm':
                    arg = g.getOptarg();
                    log.debug("CMD:Crypto method: " + arg);
                    GlobalConfig.get().setMethod(arg);
                    break;
                case 'k':
                    arg = g.getOptarg();
                    log.debug("CMD:Password: " + arg);
                    GlobalConfig.get().setPassowrd(arg);
                    break;
                case 'p':
                    arg = g.getOptarg();
                    int port = Integer.parseInt(arg);
                    log.debug("CMD:Server port: " + port);
                    GlobalConfig.get().setPort(port);
                    break;
                case 'a':
                    log.debug("CMD:OTA enforcing mode.");
                    GlobalConfig.get().setOTAEnabled(true);
                    break;
                case 'S':
                    log.debug("CMD:Server mode.");
                    GlobalConfig.get().setServerMode(true);
                    break;
                case 'L':
                    log.debug("CMD:Local mode.");
                    GlobalConfig.get().setServerMode(false);
                    break;
                case 's':
                    arg = g.getOptarg();
                    log.debug("CMD:Server address: " + arg);
                    GlobalConfig.get().setServer(arg);
                    break;
                case 'l':
                    arg = g.getOptarg();
                    int lport = Integer.parseInt(arg);
                    log.debug("CMD:Local port: " + lport);
                    GlobalConfig.get().setLocalPort(lport);
                    break;
                case 'c':
                    arg = g.getOptarg();
                    log.debug("CMD:Config file: " + arg);
                    GlobalConfig.get().setConfigFile(arg);
                    break;
                case 't':
                    arg = g.getOptarg();
                    int timeout = Integer.parseInt(arg);
                    log.debug("CMD:timeout: " + timeout);
                    GlobalConfig.get().setTimeout(timeout);
                    break;
                case 'h':
                case '?':
                default:
                    help();
                    return false;
            }
        }
        return true;
    }

    public static LocalConfig createLocalConfig() {
        LocalConfig lc = null;
        GlobalConfig.get().getLock();
        lc = new LocalConfig(GlobalConfig.get().getPassword(),
                GlobalConfig.get().getMethod(),
                GlobalConfig.get().getServer(),
                GlobalConfig.get().getPort(),
                GlobalConfig.get().getLocalPort(),
                GlobalConfig.get().isOTAEnabled(),
                GlobalConfig.get().getTimeout()
                );
        GlobalConfig.get().releaseLock();
        return lc;
    }

    private static void help()
    {
        System.out.println("Usage:\n" +
                "   -m crypto method\n" +
                "   -k password\n" +
                "   -p bind port(server)/remote port(client)\n" +
                "   -a OTA enforcing mode\n" +
                "   -l local port\n" +
                "   -s server\n" +
                "   -S server mode\n" +
                "   -L Local mode(client, default)\n" +
                "   -c config file\n" +
                "   -t timeout(unit is second)\n" +
                "   -h show help.\n");
    }
}
