/**
 *	Database Publisher tool sample. This sample monitors a target directory in file system. Upon creating a new file in directory
 *	the contents of the file are parsed and the contents of file is published to TREP as an image or update message.
 *	The file whose content is to be published is created by a SQL Trigger running on database table of interest. 
 *	A database and trigger for mySQL database is provided. Similar approach can be taken with other databases with some tweaking.
 */
 
 
public class DBPublisher {

	public static void help() {
		System.out.println("Database Publisher Sample");
		System.out.println("Usage arguments:");
		System.out.println("\tHOSTNAME - TREP ADS host:port");
		System.out.println("\tSERVICE - Publish Service Name");
		System.out.println("\tUSERNAME - Publish enabled login ID");
		System.out.println("\tDIRECTORY - Directory to monitor for files");
		System.out.println();
		
		System.out.println("\teg: java DBPublisher \"myADH1:14002\" SPRD_SRV user1 ~/tmp/DBFiles");
	}
	
	
	public static void main(String[] args) throws Exception {
		// get arguments
		if(args.length < 4)	{
			help();
			return;
		}
		
		String host = args[0];
		String service = args[1];
		String user = args[2];
		String dir = args[3];
		
		System.out.println("Starting DB Publisher with:");
		System.out.println("TREP Hostname:\t\t" + host);
		System.out.println("Service Name:\t\t" + service);
		System.out.println("Username:\t\t" + user);
		System.out.println("Monitored Directory:\t" + dir);
		
		// startup system
		
		// create a TREP publishing component
		Provider nip = new Provider(host, service, user);
		
		// create a file scanner/parser
		Scanner scan = new Scanner(nip, dir);
		scan.startScanning();
	}
}


