package org.inchain.transaction;

import java.math.BigInteger;

import org.inchain.UnitBaseTestCase;
import org.inchain.account.AccountTool;
import org.inchain.account.Address;
import org.inchain.core.Coin;
import org.inchain.crypto.ECKey;
import org.inchain.crypto.Sha256Hash;
import org.inchain.network.NetworkParams;
import org.inchain.script.Script;
import org.inchain.script.ScriptBuilder;
import org.inchain.signers.LocalTransactionSigner;
import org.inchain.utils.Hex;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

public class TranslationTest extends UnitBaseTestCase {
	
	@Autowired
	private NetworkParams network;

	@Test
	public void testTranslation() {
		
        Address addr = Address.fromP2PKHash(network, network.getSystemAccountVersion(), Hex.decode("ffdf74c494d27474def57c5cb4b41a5455705956"));

		//上次交易
		Transaction out = new Transaction(network);
		out.setHash(Sha256Hash.wrap(Hex.decode("75d58fffca9a69ba47056e435f7a5a2347a11d0093b50b415aa28e973d70640b")));
		
		Transaction tx = new Transaction(network);
		
		Script script = ScriptBuilder.createOutputScript(addr);
		
		TransactionOutput output = new TransactionOutput(out, Coin.COIN, script.getProgram());

		out.addOutput(output);
		
		//本次输入
		TransactionInput input = tx.addInput(output);

		//输出到该地址
		ECKey key = ECKey.fromPrivate(new BigInteger("16426823946378490801614451355554969482806436503112915489322677953633742147003"));
		
		Address to = AccountTool.newAddress(network, network.getSystemAccountVersion(), key);
		//添加输出
		TransactionOutput newOutput = new TransactionOutput(tx, Coin.COIN, to);
		tx.addOutput(newOutput);
		//交易类型
		tx.setVersion(to.getVersion());
		
		//签名交易
		//创建一个输入的空签名
		input.setScriptSig(ScriptBuilder.createInputScript(null, key));

		//
		final LocalTransactionSigner signer = new LocalTransactionSigner();
		signer.signInputs(tx, key);
		
		byte[] txBytes = tx.baseSerialize();
		System.out.println(Hex.encode(txBytes));
		
		Transaction verfyTx = network.getDefaultSerializer().makeTransaction(txBytes, null);
		
		verfyTx.getInput(0).getScriptSig().run(verfyTx, 0, ((TransactionInput)verfyTx.getInput(0)).getFrom().getScript());

	}
}
