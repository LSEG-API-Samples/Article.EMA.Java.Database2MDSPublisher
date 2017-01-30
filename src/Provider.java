/** Standard OMM provider class
 * 	
 */

 import java.util.*;
import com.thomsonreuters.ema.access.*;

public class Provider {

	private HashMap<String, Integer> publishedItems = null;
	private OmmProvider provider = null;
	private String serviceName;
	private int currHandle = 1;
	
	Provider(String host, String service, String user)	{
		this.serviceName = service;
		
		OmmNiProviderConfig config = EmaFactory.createOmmNiProviderConfig();
		provider = EmaFactory.createOmmProvider(config.host(host).username(user));
		publishedItems = new HashMap<String, Integer>();
	}

	
	public void publish(String itemName, java.util.Map<Integer, Integer> data) throws Exception	{
		System.out.println("Publishing: " + itemName + ", data: " + data);
		
		// publishing a Market Price message
		// create a fieldlist with provided data
		FieldList fieldList = EmaFactory.createFieldList();
		
		data.forEach((key ,value) -> {
			int fid = key.intValue();
			int dValue = value.intValue();
			// add read data into OMM fieldlist
			fieldList.add( EmaFactory.createFieldEntry().real(fid, dValue, OmmReal.MagnitudeType.EXPONENT_NEG_2));
		});
		
		// get the handle for publishing
		Integer publishHandle = publishedItems.get(itemName);
		
		if(publishHandle == null)	{
			// new item, create a new handle
			publishHandle = new Integer(currHandle++);
			// add back to map
			publishedItems.put(itemName, publishHandle);
			// publish an image message
			provider.submit(EmaFactory.createRefreshMsg().serviceName(serviceName).name(itemName)
				.state(OmmState.StreamState.OPEN, OmmState.DataState.OK, OmmState.StatusCode.NONE, "UnSolicited Refresh Completed")
				.payload(fieldList).complete(true), publishHandle.intValue());
		}
		else	{
			// existing item, publish an update
			provider.submit(EmaFactory.createUpdateMsg().serviceName(serviceName).name(itemName)
				.payload(fieldList), publishHandle.intValue());
		}
		
	}
	

}
