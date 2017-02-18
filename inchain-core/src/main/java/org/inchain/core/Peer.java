package org.inchain.core;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.inchain.crypto.Sha256Hash;
import org.inchain.message.Block;
import org.inchain.message.BlockHeader;
import org.inchain.message.GetDatasMessage;
import org.inchain.message.Message;
import org.inchain.message.NewBlockMessage;
import org.inchain.message.PingMessage;
import org.inchain.message.VersionMessage;
import org.inchain.msgprocess.DefaultMessageProcessFactory;
import org.inchain.msgprocess.MessageProcess;
import org.inchain.msgprocess.MessageProcessFactory;
import org.inchain.msgprocess.MessageProcessResult;
import org.inchain.network.NetworkParams;
import org.inchain.transaction.Transaction;
import org.inchain.utils.RandomUtil;
import org.slf4j.LoggerFactory;
import org.springframework.util.concurrent.SettableListenableFuture;

import com.google.common.base.Preconditions;

public class Peer extends PeerSocketHandler {
	
	private static final org.slf4j.Logger log = LoggerFactory.getLogger(Peer.class);
	
	//数据下载等待列表
	private static volatile Map<Sha256Hash, SettableListenableFuture<GetDataResult>> downDataFutures = new ConcurrentHashMap<Sha256Hash, SettableListenableFuture<GetDataResult>>();
			
	//异步顺序执行所有接收到的消息，以免有处理时间较长的线程阻塞，影响性能
	private ExecutorService executorService = Executors.newSingleThreadExecutor();
	
	//消息处理器工厂
	private static MessageProcessFactory messageProcessFactory = DefaultMessageProcessFactory.getInstance();

	//监听器 bengin
	//监听器 end
	private NetworkParams network;

	//节点版本信息
	private VersionMessage peerVersionMessage;
	//节点握手完成
	private boolean handshake = false;
	//节点最新高度
	private AtomicLong bestBlockHeight;
	
	private Sha256Hash startBlockDownloadHash;
	private Sha256Hash stopBlockDownloadHash;
	private SettableListenableFuture<Boolean> downloadFuture;
	
	public Peer(NetworkParams network, InetSocketAddress address) {
		this(network, new PeerAddress(address));
	}
	
	public Peer(NetworkParams network, PeerAddress peerAddress) {
		super(network, peerAddress);
		this.network = network;
		this.peerAddress = peerAddress;
	}

	@Override
	protected void processMessage(final Message message) throws Exception {
		final MessageProcess messageProcess = messageProcessFactory.getFactory(message);
		executorService.submit(new Thread(){
			public void run() {
				MessageProcessResult result = messageProcess.process(message, Peer.this);
				processMessageResult(message, result);
			};
		});
	}
	
	/**
	 * 处理消息运行结果
	 * @param message 
	 * @param result
	 */
	protected void processMessageResult(Message message, MessageProcessResult result) {
		if(result == null) {
			return;
		}
		handleAfterProcess(message, result);
		//是否成功
		if(!result.isSuccess()) {
			//如果需要回应，那么这里发送消息，一般是在消息被拒绝之后
			if(result.getReplyMessage() != null) {
				sendMessage(result.getReplyMessage());
			}
		}
	}

	/*
	 * 接收到的消息处理成功，需要做一些额外的操作，在此执行
	 */
	private void handleAfterProcess(Message message, MessageProcessResult result) {
		Sha256Hash hash = null;
		//判断是否是区块或者交易下载完成
		if(message instanceof Block || message instanceof NewBlockMessage) {
			//区块下载完成
			hash = ((Block) message).getHash();
		} else if(message instanceof Transaction) {
			//交易下载完成
			hash = ((Transaction) message).getHash();
		}
		//判断是否在下载列表中
		if(hash != null) {
			SettableListenableFuture<GetDataResult> future = downDataFutures.get(hash);
			if(future != null) {
				downDataFutures.remove(hash);
				GetDataResult getDataResult = new GetDataResult(message, result.isSuccess());
				future.set(getDataResult);
			}
		}
	}

	/**
	 * 发送获取数据消息，并获取相应返回信息
	 * @param getdata
	 * @return Future<GetDataResult>
	 */
	public Future<GetDataResult> sendGetDataMessage(GetDatasMessage getdata) {
		//获取数据 ，仅一条
        Preconditions.checkArgument(getdata.getInvs().size() == 1);
        SettableListenableFuture<GetDataResult> future = new SettableListenableFuture<GetDataResult>();
        downDataFutures.put(getdata.getInvs().get(0).getHash(), future);
        sendMessage(getdata);
        return future;
    }
	
	/**
	 * 监听区块下载
	 * @param startHash
	 * @param stopHash
	 */
	public void setListenerGetBlocks(Sha256Hash startHash, Sha256Hash stopHash) {
		this.startBlockDownloadHash = startHash;
		this.stopBlockDownloadHash = stopHash;
	}

	/**
	 * 等待区块下载完成
	 */
	public void waitBlockDownComplete() {
		 downloadFuture = new SettableListenableFuture<Boolean>();
		 try {
			downloadFuture.get(60, TimeUnit.SECONDS);
		} catch (Exception e) {
			log.warn("下载区块等待超时 {}", e.getMessage());
		}
	}
	
	/**
	 * 通知区块下载完成
	 */
	public void notifyDownloadComplete() {
		if(downloadFuture != null) {
			downloadFuture.set(true);
		}
	}
	
	@Override
	public int getMaxMessageSize() {
		return Message.MAX_SIZE;
	}
	
	@Override
	public void connectionClosed() {
		log.info("connectionClosed");
	}

	@Override
	public void connectionOpened() {
		log.info("connectionOpened {}", this);
		//发送版本信息
		BlockHeader bestBlock = network.getBestBlockHeader().getBlockHeader();
		sendMessage(new VersionMessage(network, bestBlock.getHeight(), bestBlock.getHash(), getPeerAddress()));
	}
	
	/**
	 * 节点最新高度加1并返回最新高度
	 * @return long
	 */
	public long addAndGetBestBlockHeight() {
		return bestBlockHeight.incrementAndGet();
	}
	
	/**
	 * 获取节点最新高度
	 * @return long
	 */
	public long getBestBlockHeight() {
		return bestBlockHeight.get();
	}
	
	@Override
	public String toString() {
		return (peerAddress == null ? "":peerAddress.toString()) + (peerVersionMessage == null ? "":peerVersionMessage.toString());
	}

	public void ping() {
		sendMessage(new PingMessage(RandomUtil.randomLong()));
	}
	
	public PeerAddress getPeerAddress() {
		return peerAddress;
	}

	public NetworkParams getNetwork() {
		return network;
	}
	
	public VersionMessage getPeerVersionMessage() {
		return peerVersionMessage;
	}
	public void setPeerVersionMessage(VersionMessage peerVersionMessage) {
		this.peerVersionMessage = peerVersionMessage;
		bestBlockHeight = new AtomicLong(peerVersionMessage.bestHeight);
	}

	public boolean isHandshake() {
		return handshake;
	}

	public void setHandshake(boolean handshake) {
		this.handshake = handshake;
	}
}
