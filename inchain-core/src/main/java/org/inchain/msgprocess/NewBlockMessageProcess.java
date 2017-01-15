package org.inchain.msgprocess;

import org.inchain.SpringContextUtils;
import org.inchain.core.Peer;
import org.inchain.filter.InventoryFilter;
import org.inchain.kits.PeerKit;
import org.inchain.message.BlockMessage;
import org.inchain.message.InventoryItem;
import org.inchain.message.InventoryItem.Type;
import org.inchain.store.BlockHeaderStore;
import org.inchain.message.InventoryMessage;
import org.inchain.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 新区块广播消息
 * 接收到新的区块之后，验证该区块是否合法，如果合法则进行收录并转播出去
 * 验证该区块是否合法的流程为：
 * 1、该区块基本的验证（包括区块的时间、大小、交易的合法性，梅克尔树根是否正确）。
 * 2、该区块的广播人是否是合法的委托人。
 * 3、该区块是否衔接最新区块，不允许分叉区块。
 * @author ln
 *
 */
@Service
public class NewBlockMessageProcess extends BlockMessageProcess {

	private Logger log = LoggerFactory.getLogger(getClass());
	
	@Autowired
	private PeerKit peerKit;
	
	/**
	 * 接收到区块消息，进行区块合法性验证，如果验证通过，则收录，然后转发区块
	 */
	@Override
	public MessageProcessResult process(Message message, Peer peer) {
		//验证新区块打包的人是否合法
		//TODO

		BlockMessage blockMessage = (BlockMessage) message;
		
		peer.getNetwork().setBestHeight(blockMessage.getBlockStore().getHeight());
		
		if(log.isDebugEnabled()) {
			log.debug("new block : {}", blockMessage.getBlockStore().getHash());
		}
		
		super.process(message, peer);
		
		//区块变化监听器
		if(peerKit.getBlockChangedListener() != null) {
			BlockHeaderStore localBestBlockHeader = blockMessage.getBlockStore();
			peerKit.getBlockChangedListener().onChanged(-1l, localBestBlockHeader.getHeight(), null, localBestBlockHeader.getHash());
		}
		
		//转发新区块消息
		if(log.isDebugEnabled()) {
			log.debug("new block {} saved", blockMessage.getBlockStore().getHash());
		}
		
		if(peer.getBlockDownendListener() != null) {
			peer.getBlockDownendListener().downend(blockMessage.getBlockStore().getHeight());
		} else {
			InventoryFilter filter = SpringContextUtils.getBean(InventoryFilter.class);
			filter.insert(blockMessage.getBlockStore().getHash().getBytes());
		}
		//转发
		InventoryItem item = new InventoryItem(Type.NewBlock, blockMessage.getBlockStore().getHash());
		InventoryMessage invMessage = new InventoryMessage(peer.getNetwork(), item);
		peerKit.broadcastMessage(invMessage, peer);
		
		return null;
	}
}
