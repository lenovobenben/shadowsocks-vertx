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
package shadowsocks.nio.tcp;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import shadowsocks.util.LocalConfig;

import shadowsocks.crypto.SSCrypto;
import shadowsocks.crypto.CryptoFactory;
import shadowsocks.crypto.CryptoException;

import shadowsocks.auth.SSAuth;
import shadowsocks.auth.HmacSHA1;
import shadowsocks.auth.AuthException;

/**
 * TcpRelay model
 * A--B--C--D
 * A is program B is SSLocal C is SSServer D is target
 * For B/C we hava 2 SocketChannels local/remote
 * For SSLocal local is the connection between A and B, remote is the connection between B and C
 * For SSServer local is the connection between B and C, remote is the connection between C and D.
 */

public abstract class TcpWorker implements Runnable {

    // Subclass should implement these methods.
    /**
     * Transform data from source to target.
     */
    protected abstract boolean send(SocketChannel source, SocketChannel target, int direct) throws IOException,CryptoException,AuthException;
    /**
     * Handle stage.
     */
    protected abstract void handleStage(int stage) throws IOException,CryptoException, AuthException;
    /**
     * Init server/local special fields.
     */
    protected abstract void init() throws Exception;

    protected final static int PARSE_HEADER = 0;
    protected final static int BEFORE_TCP_RELAY = 1;
    protected final static int AFTER_TCP_RELAY = 2;

    // Common work
    protected static Logger log = LogManager.getLogger(TcpWorker.class.getName());

    private SocketChannel mLocal;

    protected Session mSession;

    protected SSCrypto mCryptor;

    protected LocalConfig mConfig;

    // For OTA
    // Store the data to do one time auth
    protected ByteArrayOutputStream mStreamUpData;
    protected boolean mOneTimeAuth = false;
    protected SSAuth mAuthor;
    protected int mChunkCount = 0;


    protected void doTcpRelay(Selector selector, SocketChannel local, SocketChannel remote) throws IOException,InterruptedException,CryptoException,AuthException
    {
        local.configureBlocking(false);
        remote.configureBlocking(false);

        local.register(selector, SelectionKey.OP_READ);
        remote.register(selector, SelectionKey.OP_READ);

        boolean finish = false;

        while(true){
            int n = selector.select(1000);
            if (n == 0){
                if (mSession.isTimeout()) {
                    log.debug(mSession.getID() + ": close timeout worker");
                    break;
                }
                if (mSession.hasStreamUpBufferedData()) {
                    log.debug(mSession.getID() + ": Resend stream up data");
                    BufferHelper.send(remote, null, mSession.getStreamUpBufferedData());
                }
                if (mSession.hasStreamDownBufferedData()) {
                    log.debug(mSession.getID() + ": Resend stream down data");
                    BufferHelper.send(local, null, mSession.getStreamDownBufferedData());
                }
                continue;
            }
            Iterator it = selector.selectedKeys().iterator();
            while (it.hasNext()) {
                SelectionKey key = (SelectionKey)it.next();
                if (key.isReadable()) {
                    mSession.updateActiveTime();
                    SocketChannel channel = (SocketChannel)key.channel();
                    if (channel.equals(local)) {
                        finish = send(local, remote, Session.LOCAL2REMOTE);
                    }else{
                        finish = send(remote, local, Session.REMOTE2LOCAL);
                    }
                }
                it.remove();
            }
            if (finish)
                break;
        }
    }

    protected void TcpRelay(SocketChannel local)
    {
        int CONNECT_TIMEOUT = 5000;

        try(SocketChannel remote = SocketChannel.open();
                Selector selector = Selector.open();)
        {
            mSession.set(local, true);
            mSession.set(remote, false);

            handleStage(PARSE_HEADER);

            log.info(mSession.getID() + ": connecting " + mConfig.target + " from " + local.socket().getLocalSocketAddress());

            remote.socket().setTcpNoDelay(true);
            remote.socket().connect(mConfig.remoteAddress, CONNECT_TIMEOUT);

            handleStage(BEFORE_TCP_RELAY);

            doTcpRelay(selector, local, remote);

            handleStage(AFTER_TCP_RELAY);

        }catch(SocketTimeoutException e){
            log.warn("Connect " + mConfig.remoteAddress + " timeout.");
        }catch(InterruptedException e){
            //ignore
        }catch(IOException | CryptoException | AuthException e){
            mSession.dump(log, e);
        }

    }


    @Override
    public void run(){
        //make sure this channel could be closed
        try(SocketChannel local = mLocal){

            mSession = new Session();

            // for decrypt/encrypt
            mCryptor = CryptoFactory.create(mConfig.method, mConfig.password);

            // for one time auth
            mAuthor = new HmacSHA1();

            mStreamUpData = new ByteArrayOutputStream();

            //Init subclass special field.
            init();

            TcpRelay(local);
        }catch(Exception e){
            mSession.dump(log, e);
        }finally{
            mSession.destory();
        }
    }

    public TcpWorker(SocketChannel sc, LocalConfig lc){
        mLocal = sc;
        mConfig = lc;
    }
}
