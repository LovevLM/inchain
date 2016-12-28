package org.inchain.utils;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;

public class IpUtil {

	/** 
     * 多IP处理，可以得到最终ip 
     * @return 
     */  
    public static Set<String> getIps() {  
    	//返回本机所有的IP地址 ，包括了内网和外网的IP4地址
    	Set<String> ips = new HashSet<String>();
        try {
            Enumeration<NetworkInterface> netInterfaces = NetworkInterface.getNetworkInterfaces();  
            InetAddress ip = null;
            while (netInterfaces.hasMoreElements()) {
                NetworkInterface ni = netInterfaces.nextElement();  
                Enumeration<InetAddress> address = ni.getInetAddresses();  
                while (address.hasMoreElements()) {  
                    ip = address.nextElement();  
                    if (!ip.isSiteLocalAddress() && !ip.isLoopbackAddress()  
                            && ip.getHostAddress().indexOf(":") == -1) {// 外网IP  
                    	ips.add(ip.getHostAddress());  
                        break;  
                    } else if (ip.isSiteLocalAddress()  
                            && !ip.isLoopbackAddress()  
                            && ip.getHostAddress().indexOf(":") == -1) {// 内网IP  
                    	ips.add(ip.getHostAddress());  
                    }  
                }  
            }  
        } catch (SocketException e) {  
            e.printStackTrace();
        }
        return ips;
    }
    
}
