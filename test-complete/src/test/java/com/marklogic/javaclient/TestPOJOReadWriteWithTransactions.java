package com.marklogic.javaclient;

import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.marklogic.client.DatabaseClient;
import com.marklogic.client.DatabaseClientFactory;
import com.marklogic.client.Transaction;
import com.marklogic.client.DatabaseClientFactory.Authentication;
import com.marklogic.client.pojo.PojoPage;
import com.marklogic.client.pojo.PojoRepository;

public class TestPOJOReadWriteWithTransactions extends BasicJavaClientREST{

	private static String dbName = "TestPOJORWTransDB";
	private static String [] fNames = {"TestPOJORWTransDB-1"};
	private static String restServerName = "REST-Java-Client-API-Server";
	private static int restPort = 8011;
	private  DatabaseClient client ;

	@BeforeClass
	public static void setUpBeforeClass() throws Exception {
		System.setProperty("org.apache.commons.logging.simplelog.log.org.apache.http.wire", "debug");
		System.out.println("In setup");
		setupJavaRESTServer(dbName, fNames[0], restServerName,restPort);
	}

	@AfterClass
	public static void tearDownAfterClass() throws Exception {
		System.out.println("In tear down" );
		tearDownJavaRESTServer(dbName, fNames, restServerName);
	}

	@Before
	public void setUp() throws Exception {
		client = DatabaseClientFactory.newClient("localhost", restPort, "rest-admin", "x", Authentication.DIGEST);
	}

	@After
	public void tearDown() throws Exception {
		// release client
		client.release();
	}

	public Artifact getArtifact(int counter){
		Company acme = new Company();
		acme.setName("Acme "+counter+", Inc.");
		acme.setWebsite("http://www.acme"+counter+".com");
		acme.setLatitude(41.998+counter);
		acme.setLongitude(-87.966+counter);
		Artifact cogs = new Artifact();
		cogs.setId(counter);
		cogs.setName("Cogs "+counter);
		cogs.setManufacturer(acme);
		cogs.setInventory(1000+counter);

		return cogs;
	}
    public void validateArtifact(Artifact art)
    {
    assertNotNull("Artifact object should never be Null",art);
    assertNotNull("Id should never be Null",art.id);
    assertTrue("Inventry is always greater than 1000", art.getInventory()>1000);
    }
    //This test is to persist a simple design model objects in ML, read from ML, delete all
	// Issue 104 for unable to have transaction in count,exists, delete methods
    @Test
	public void testPOJOWriteWithTransaction() throws Exception {
		PojoRepository<Artifact,Long> products = client.newPojoRepository(Artifact.class, Long.class);
		Transaction t= client.openTransaction();
		//Load more than 100 objects
		try{
		for(int i=1;i<112;i++){
			products.write(this.getArtifact(i),t);
		}
//		assertEquals("Total number of object recods",111, products.count());
		for(long i=1;i<112;i++){
//			assertTrue("Product id "+i+" does not exist",products.exists(i));
			this.validateArtifact(products.read(i,t));
		}
	
		}catch(Exception e){
			throw e;
		}finally{
			t.rollback();
		}
		
		for(long i=1;i<112;i++){
			assertFalse("Product id exists ?",products.exists(i));
		}
	}
	//This test is to persist objects into different collections, read documents based on Id and delete single object based on Id
	@Test
	public void testPOJOWriteWithTransCollection() throws Exception {
		PojoRepository<Artifact,Long> products = client.newPojoRepository(Artifact.class, Long.class);
		//Load more than 110 objects into different collections
		Transaction t= client.openTransaction();
		try{
		for(int i=112;i<222;i++){
			if(i%2==0){
			products.write(this.getArtifact(i),t,"even","numbers");
			}
			else {
				products.write(this.getArtifact(i),t,"odd","numbers");
			}
		}
		}catch(Exception e){throw e;}
		finally{t.commit();}
		assertEquals("Total number of object recods",110, products.count("numbers"));
		assertEquals("Collection even count",55,products.count("even"));
		assertEquals("Collection odd count",55,products.count("odd"));
		for(long i=112;i<222;i++){
			// validate all the records inserted are readable
			assertTrue("Product id "+i+" does not exist",products.exists(i));
			this.validateArtifact(products.read(i));
		}
//		t= client.openTransaction();
		
		products.delete((long)112);
		assertFalse("Product id 112 exists ?",products.exists((long)112));
		products.deleteAll();
		//see any document exists
		for(long i=112;i<222;i++){
			assertFalse("Product id "+i+" exists ?",products.exists(i));
		}
		//see if it complains when there are no records
		products.delete((long)112);
		products.deleteAll();
	}
	//This test is to read objects into pojo page based on Ids 
		// until #103 is resolved	@Test
		public void testPOJOWriteWithPojoPage() {
			PojoRepository<Artifact,Long> products = client.newPojoRepository(Artifact.class, Long.class);
			//Load more than 110 objects into different collections
			products.deleteAll();
			Long[] ids= new Long[111];
			int j=0;
			for(int i=222;i<333;i++){
				ids[j] =(long) i;j++;
				if(i%2==0){
				products.write(this.getArtifact(i),"even","numbers");
				}
				else {
					products.write(this.getArtifact(i),"odd","numbers");
				}
			}
			assertEquals("Total number of object recods",111, products.count("numbers"));
			assertEquals("Collection even count",56,products.count("even"));
			assertEquals("Collection odd count",55,products.count("odd"));
			
			System.out.println("Default Page length setting on docMgr :"+products.getPageLength());
			assertEquals("Default setting for page length",50,products.getPageLength());
			products.setPageLength(100);
//			assertEquals("explicit setting for page length",1,products.getPageLength());
			PojoPage<Artifact> p= products.read(ids);
			// test for page methods
		//Issue-	assertEquals("Number of records",1,p.size());
			System.out.println("Page size"+p.size());
//			assertEquals("Starting record in first page ",1,p.getStart());
			System.out.println("Starting record in first page "+p.getStart());
			
//			assertEquals("Total number of estimated results:",111,p.getTotalSize());
			System.out.println("Total number of estimated results:"+p.getTotalSize());
//			assertEquals("Total number of estimated pages :",111,p.getTotalPages());
			System.out.println("Total number of estimated pages :"+p.getTotalPages());
			assertFalse("Is this First page :",p.isFirstPage());//this is bug
			assertFalse("Is this Last page :",p.isLastPage());
			assertTrue("Is this First page has content:",p.hasContent());
			//		Need the Issue #75 to be fixed  
			assertTrue("Is first page has previous page ?",p.hasPreviousPage());
			long pageNo=1,count=0;
			do{
				count=0;
				if(pageNo >1){ 
					assertFalse("Is this first Page", p.isFirstPage());
					assertTrue("Is page has previous page ?",p.hasPreviousPage());
				}
				Iterator<Artifact> itr = p.iterator();
			while(itr.hasNext()){
				this.validateArtifact(p.iterator().next());
				count++;
			}
//			assertEquals("document count", p.size(),count);
			System.out.println("Is this Last page :"+p.hasContent()+p.isLastPage());
			pageNo = pageNo + p.getPageSize();
			}while((p.isLastPage()) && p.hasContent());
			assertTrue("page count is 111 ",pageNo == p.getTotalPages());
			assertTrue("Page has previous page ?",p.hasPreviousPage());
			assertEquals("page size", 1,p.getPageSize());
			assertEquals("document count", 111,p.getTotalSize());
			assertFalse("Page has any records ?",p.hasContent());
			
			
			products.deleteAll();
			//see any document exists
			for(long i=112;i<222;i++){
				assertFalse("Product id "+i+" exists ?",products.exists(i));
			}
			//see if it complains when there are no records
		}
	@Test
	public void testPOJOWriteWithPojoPageReadAll() throws Exception {
		PojoRepository<Artifact,Long> products = client.newPojoRepository(Artifact.class, Long.class);
		//Load more than 110 objects into different collections
		Transaction t= client.openTransaction();
		PojoPage<Artifact> p;
		try{
		for(int i=222;i<333;i++){
		if(i%2==0){
			products.write(this.getArtifact(i),t,"even","numbers");
			}
		else {
				products.write(this.getArtifact(i),t,"odd","numbers");
			}
		}
		//issue# 104
//		assertEquals("Total number of object recods",111, products.count("numbers"));
//		assertEquals("Collection even count",56,products.count("even"));
//		assertEquals("Collection odd count",55,products.count("odd"));
		
		System.out.println("Default Page length setting on docMgr :"+products.getPageLength());
		assertEquals("Default setting for page length",50,products.getPageLength());
		products.setPageLength(25);
		assertEquals("explicit setting for page length",25,products.getPageLength());
	    p= products.readAll(1,t);
		// test for page methods
		assertEquals("Number of records",25,p.size());
		System.out.println("Page size"+p.size());
		assertEquals("Starting record in first page ",1,p.getStart());
		System.out.println("Starting record in first page "+p.getStart());
		
		assertEquals("Total number of estimated results:",111,p.getTotalSize());
		System.out.println("Total number of estimated results:"+p.getTotalSize());
		assertEquals("Total number of estimated pages :",5,p.getTotalPages());
		System.out.println("Total number of estimated pages :"+p.getTotalPages());
		assertTrue("Is this First page :",p.isFirstPage());
		assertFalse("Is this Last page :",p.isLastPage());
		assertTrue("Is this First page has content:",p.hasContent());
		//		Need the Issue #75 to be fixed  
		assertTrue("Is first page has previous page ?",p.hasPreviousPage());
		long pageNo=1,count=0;
		do{
			count=0;
			p= products.readAll(pageNo,t);
			
			if(pageNo >1){ 
				assertFalse("Is this first Page", p.isFirstPage());
				assertTrue("Is page has previous page ?",p.hasPreviousPage());
			}
			
		while(p.iterator().hasNext()){
			this.validateArtifact(p.iterator().next());
			count++;
		}
		assertEquals("document count", p.size(),count);
		
		pageNo = pageNo + p.getPageSize();
		}while(!(p.isLastPage()) && pageNo < p.getTotalSize());
		
		assertTrue("Page has previous page ?",p.hasPreviousPage());
		assertEquals("page size", 11,p.size());
		assertEquals("document count", 111,p.getTotalSize());
		}catch(Exception e){
			throw e;
		}finally{
			t.rollback();
		}
		p=products.readAll(1);
		assertFalse("Page has any records ?",p.hasContent());
		
	}

	@Test
	public void testPOJOSearchWithCollectionsandTransaction() throws Exception {
		PojoRepository<Artifact,Long> products = client.newPojoRepository(Artifact.class, Long.class);
		PojoPage<Artifact> p;
		Transaction t= client.openTransaction();
		//Load more than 111 objects into different collections
		try{
		for(int i=1;i<112;i++){
			if(i%2==0){
			products.write(this.getArtifact(i),t,"even","numbers");
			}
			else {
				products.write(this.getArtifact(i),"odd","numbers");
			}
		}
		assertEquals("Total number of object recods",56, products.count("numbers"));
		assertEquals("Collection even count",0,products.count("even"));
		assertEquals("Collection odd count",56,products.count("odd"));
		
		products.setPageLength(10);
		long pageNo=1,count=0;
		do{
			count =0;
			p = products.search(pageNo, "even");
			while(p.iterator().hasNext()){
				Artifact a =p.iterator().next();
				validateArtifact(a);
				assertTrue("Artifact Id is even", a.getId()%2==0);
				count++;
			}
			assertEquals("Page size",count,p.size());
			pageNo=pageNo+p.getPageSize();
		}while(!p.isLastPage() && pageNo<p.getTotalSize());
		assertEquals("total no of pages",0,p.getTotalPages());
		do{
			count =0;
			p = products.search(pageNo,t, "even");
			while(p.iterator().hasNext()){
				Artifact a =p.iterator().next();
				validateArtifact(a);
				assertTrue("Artifact Id is even", a.getId()%2==0);
				count++;
			}
			assertEquals("Page size",count,p.size());
			pageNo=pageNo+p.getPageSize();
		}while(!p.isLastPage() && pageNo<p.getTotalSize());
		assertEquals("total no of pages",6,p.getTotalPages());
		
		pageNo=1;
		do{
			count =0;
			p = products.search(1,t, "odd");
			while(p.iterator().hasNext()){
				Artifact a =p.iterator().next();
				assertTrue("Artifact Id is even", a.getId()%2 !=0);
				validateArtifact(a);
				products.delete(a.getId());
				count++;
			}
//			assertEquals("Page size",count,p.size());
			pageNo=pageNo+p.getPageSize();
			
		}while(!p.isLastPage() );
		
		assertEquals("Total no of documents left",0,products.count());
		}catch(Exception e){
			throw e;
		}finally{
			t.rollback();
		}
		//see any document exists
		assertFalse("all the documents are deleted",products.exists((long)12));
	}
	
}