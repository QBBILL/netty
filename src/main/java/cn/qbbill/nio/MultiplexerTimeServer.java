package cn.qbbill.nio;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.Iterator;
import java.util.Set;

/**
 * Created by 钱斌 on 2016/10/14.
 */
public class MultiplexerTimeServer implements Runnable {

    private Selector selector;

    private ServerSocketChannel serverChannel;

    private volatile boolean stop;

    /**
     * 初始化多路复用器,绑定监听端口
     *
     * @param port
     */
    public MultiplexerTimeServer(int port) {
        try {
            selector = Selector.open();
            serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().bind(new InetSocketAddress(port), 1024);
            serverChannel.register(selector, SelectionKey.OP_ACCEPT);
            System.out.println("The time server is start in port: " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        this.stop = true;
    }


    public void run() {
        while (!stop) {
            try {
                //It returns only after at least one channel is selected, this selector's wakeup method is invoked, the current thread is interrupted, or the given timeout period expires, whichever comes first.
                selector.select(1000);
                //SelectionKey A token representing the registration of a SelectableChannel with a Selector.
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> it = selectedKeys.iterator();
                SelectionKey key = null;
                while (it.hasNext()) {
                    key = it.next();
                    it.remove();
                    try {
                        handleInput(key);
                    } catch (Exception e) {
                        if (key != null) {
                            //取消注册
                            key.cancel();
                            if (key.channel() != null) {
                                //关闭通道
                                key.channel().close();
                            }
                        }
                    }

                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        //关闭selector,所有注册在上面的channel和pipe都会自动关闭
        if (selector != null) {
            try {
                selector.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


    }

    private void handleInput(SelectionKey key) throws IOException {
        if (key.isValid()) {
            //处理新接入的请求信息,对于底层而言就是完成了TCP的三次握手,tcp物理链路正式建立
            if (key.isAcceptable()) {
                //接受新请求
                ServerSocketChannel ssc = (ServerSocketChannel) key.channel();
                SocketChannel sc = ssc.accept();
                sc.configureBlocking(false);
                // add the new connection to the selector
                sc.register(selector, SelectionKey.OP_READ);
            }
            //读取客户端的请求消息
            if (key.isReadable()) {
                //read the data
                SocketChannel sc = (SocketChannel) key.channel();
                ByteBuffer readBuffer = ByteBuffer.allocate(1024);
                int readBytes = sc.read(readBuffer);//The number of bytes read, possibly zero
                if (readBytes > 0) {
                    readBuffer.flip();//1. 把limit设置为当前的position值 2. 把position设置为0
                    byte[] bytes = new byte[readBuffer.remaining()];//The number of elements remaining in this buffer
                    readBuffer.get(bytes);//ansfers bytes from this buffer into the given destination array
                    String body = new String(bytes, "UTF-8");
                    System.out.println("The time server receive order : " + body);
                    String currentTime = "QUERY TIME ORDER".equalsIgnoreCase(body) ?
                            new java.util.Date(System.currentTimeMillis()).toString()
                            : "BAD ORDER";
                    doWrite(sc, currentTime);
                } else if (readBytes < 0) {
                    //链路关闭
                    key.cancel();
                    sc.close();
                } else {
                    ;//读到0字节,忽略
                }


            }
        }

    }

    private void doWrite(SocketChannel channel, String response) throws IOException {
        if (response != null && response.trim().length() > 0) {
            byte[] bytes = response.getBytes();
            ByteBuffer writeBuffer = ByteBuffer.allocate(bytes.length);
            writeBuffer.put(bytes);//transfers the entire content of the given source byte array into this buffer
            writeBuffer.flip();//The limit is set to the current position and then the position is set to zero
            channel.write(writeBuffer);//Writes a sequence of bytes to this channel from the given buffer.
        }
    }
}
