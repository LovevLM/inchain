package org.inchain.transaction;

import org.inchain.account.Account;
import org.inchain.account.AccountBody;
import org.inchain.account.AccountBody.ContentType;
import org.inchain.core.BroadcastResult;
import org.inchain.core.KeyValuePair;
import org.inchain.crypto.ECKey;
import org.inchain.crypto.Sha256Hash;
import org.inchain.kits.AccountKit;
import org.inchain.kits.AppKit;
import org.inchain.kits.PeerKit;
import org.inchain.mempool.MempoolContainer;
import org.inchain.network.NetworkParams;
import org.inchain.transaction.business.CertAccountRegisterTransaction;
import org.inchain.utils.Hex;
import org.inchain.validator.TransactionValidator;
import org.inchain.validator.TransactionValidatorResult;
import org.inchain.validator.ValidatorResult;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class MakeCertAccountRegisterTransaction {
	
	public static void main(String[] args) throws Exception {
		
		String[] xmls = new String[] { "classpath:/applicationContext-testnet.xml", "classpath:/applicationContext.xml" };

		ClassPathXmlApplicationContext springContext = new ClassPathXmlApplicationContext(xmls);
		
		springContext.start();
		
		NetworkParams network = springContext.getBean(NetworkParams.class);
		
		AppKit appKit = springContext.getBean(AppKit.class);
		appKit.start();
		
		AccountKit accountKit = springContext.getBean(AccountKit.class);
		
		try {
			Account managerAccount = accountKit.getAccountList().get(0);
			
			ECKey[] eckeys = managerAccount.decryptionTr("inchain123");
			
			if(eckeys == null) {
				return;
			}
			
			Sha256Hash mgtx = Sha256Hash.wrap(Hex.decode("9c890b3c4a3dd4599b445e3d40034bc5c289df7e37e391f2648124f2c273d608"));
			
			KeyValuePair[] values = {
					new KeyValuePair(ContentType.NAME, "食品测试公司"),
			};
			AccountBody body = new AccountBody(values);
			
			Account account = accountKit.createNewCertAccount("ssssss0", "ssssss1", body);
			System.out.println("base58 : " + account.getAddress().getBase58());
			System.out.println("hash160: " + Hex.encode(account.getAddress().getHash160()));
			
			CertAccountRegisterTransaction tx = new CertAccountRegisterTransaction(network, account.getAddress().getHash160(), account.getMgPubkeys(), account.getTrPubkeys(), account.getBody());
			
			System.out.println("mgtx is : "+mgtx);
			
			tx.calculateSignature(mgtx, eckeys[0], eckeys[1]);
			
			tx.verify();
			tx.verifyScript();
			
			//序列化和反序列化
			byte[] txContent = tx.baseSerialize();
			System.out.println("tx id is :" + tx.getHash());
			
			CertAccountRegisterTransaction rtx = new CertAccountRegisterTransaction(network, txContent, 0);
			
			rtx.verify();
			rtx.verifyScript();
			
			TransactionValidator transactionValidator = springContext.getBean(TransactionValidator.class);
			ValidatorResult<TransactionValidatorResult> rs = transactionValidator.valDo(rtx, null);
			System.out.println(rs.getResult());
			
			System.out.println(Hex.encode(rtx.baseSerialize()));
			System.out.println("tx id is :" +rtx.getHash());
			System.out.println(rtx.getBody());

			MempoolContainer.getInstace().add(rtx);
			
			PeerKit peerKit = springContext.getBean(PeerKit.class);
			
			BroadcastResult res = peerKit.broadcast(rtx).get();
			System.out.println(res);
			
			Thread.sleep(20000l);
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			springContext.close();
			System.exit(0);
		}
	}
}
