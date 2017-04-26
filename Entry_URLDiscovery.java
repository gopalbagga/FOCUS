/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package focus;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import javafx.application.Platform;
import javafx.scene.control.TextArea;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
/**
 *
 * @author Rakesh
 */
public class Entry_URLDiscovery {
    static int id=1;
    static ArrayList<String> allurllist=new ArrayList<>();
    static ArrayList<String> allquestionlist=new ArrayList<>();
    String[] divs={"t_fsz","post_body","inner","postmsg"};
    TextArea text_area;
    
    Filter filter;
    String url,host,schema,index_text,thread_text;
    ArrayList<String> external_urls=new ArrayList<>();
    String redirectedUrl; //to store destination url after redirection
    Connection con;
    Statement DB = null;
    String exclude_parameters[]={"action=","fromuid=","from=","page=",";all","sort=", "extra=", "filter=","lastpage=","orderby=","digest=","dateline=","specialType=","typeId=","prefix=","sortby=","detect=","order="};

    
    public Entry_URLDiscovery(TextArea text_area,String url,Filter filter) throws IOException,ClassNotFoundException, SQLException{
        
        this.text_area=text_area;
        
        Class.forName("com.mysql.jdbc.Driver");  
        con=DriverManager.getConnection("jdbc:mysql://localhost:3306/crawler","root",""); 
        DB=con.createStatement();
        
        
        this.filter=filter;
        if(StringUtils.countMatches(url, "/")>3) //if given url contains path then remove it
            url=url.substring(0, StringUtils.ordinalIndexOf(url, "/", 3));
        this.url=url;
        schema=url.substring(0,url.indexOf("/")+2); //http or https ??
            }
    
    
    
    
    public String get_entry_url() throws IOException,SQLException
    {        
                
 
        
           ArrayList<String> url_paths=get_url_paths(url); //get all url paths 
           String matched_baseline=baseline_url(url_paths);
           if("NOT_FOUND".equals(matched_baseline)){
               if(checksubdomains().length()>2)
                   return checksubdomains();
               else
                    matched_baseline="";
           }
           
           String entry_url=schema+host+matched_baseline;
           return entry_url;
    }
    
    public String checksubdomains() // return subdomain where forum is hosted
    {
        String subdomain_name;
        
        for(String i : external_urls)
        {
            if(i.contains("://")&&i.contains(".")){
            subdomain_name=i.substring(i.indexOf("/")+2, i.indexOf("."));
            if(subdomain_name.matches("forums")|subdomain_name.matches("forum")|subdomain_name.matches("community")|subdomain_name.matches("bbs")|subdomain_name.matches("discus")|subdomain_name.matches("board")|subdomain_name.matches("boards"))
            {
                return i;
            }
            }
        }
        return "";
    }
    
    
 
    
    public ArrayList<String> get_url_paths(String url) throws IOException,SQLException
    {
        
        System.out.println("\n URL received in get_url_path > "+url);
        
        System.setProperty("http.proxyHost", "127.0.0.1");      //for debugging purpose
        System.setProperty("http.proxyPort", "8080");           //for debugging purpose
        ArrayList<String> extracted_urls_path=new ArrayList<>();
        String user_url=url;
        String host;
        String urlwithhost="",urlwithouthost="";
        
        //URL should end with "/" for below codes to run 
        
        if(!user_url.endsWith("/")&&!(user_url.contains("&")))
        {
            user_url=user_url+"/";
        }
        
        int slashslash = user_url.indexOf("//") + 2; //SO
        host = user_url.substring(slashslash, user_url.indexOf('/', slashslash)); // SO,  Extract hostname from given URL
        this.host=host;
        
        if(user_url.endsWith("/")){     // to remove ending slash to avoid 404
            user_url=user_url.substring(0,user_url.length()-1);
        }
        
        
        Document doc = Jsoup.connect(user_url).userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
     .followRedirects(true).timeout(30000).get();
                
        String redirect_url=null; //to store URL of HTML redirects
        Elements meta = doc.select("html head meta");
        if (meta.attr("http-equiv").contains("REFRESH")|meta.attr("http-equiv").contains("refresh")|meta.attr("HTTP-EQUIV").contains("REFRESH")|meta.attr("HTTP-EQUIV").contains("refresh")) //For HTML Redirects
        { 
            redirect_url = meta.attr("content").split("=")[1];
            redirect_url=redirect_url.replace("'", ""); // remove ' from URL
            redirect_url=redirect_url.replace("\"", ""); // remove " from URL
            String new_url=schema+host+redirect_url;
            doc = Jsoup.connect(new_url).cookie("test","test").userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
     .followRedirects(true).timeout(300000).get();
        }
        
 
        Elements links = doc.select("a[href]"); // a with href
        
        
        for(int i=0;i<links.size();i++) //extracting, filtering and storing links in array
        {

            //Ignore links that doesn't contain anchor text
            
            if(links.get(i).text().length()<2)
            {
                continue;
            }
            
            
            for(String exclude : exclude_parameters)    //check if the url contains excluded strings
                {
                    if(links.get(i).text().contains(exclude)){
                        links.get(i).text("");
                    }
                }
            
            if(links.get(i).text().equals(""))
            {
                continue;
            }
            
            //Remove ' to avoid SQL errors 
            
            if(links.get(i).text().contains("'")){ 
                links.get(i).text(links.get(i).text().replaceAll("'", "\""));
            }
                
            if(links.get(i).absUrl("href").contains("https://"+host) | links.get(i).absUrl("href").contains("http://"+host)|links.get(i).absUrl("href").contains("https://"+"www."+host) | links.get(i).absUrl("href").contains("http://"+"www."+host)) // www cz sometimes URL's in pages starts with www and we might miss them as external links
            {
                urlwithhost=links.get(i).absUrl("href");
 
                if(urlwithhost.startsWith("http://"))
                {
                    urlwithouthost=urlwithhost.substring("http://".length()+host.length(),urlwithhost.length());
                }   
                   
                else if(urlwithhost.startsWith("https://"))
                {
                    urlwithouthost=urlwithhost.substring("https://".length()+host.length(),urlwithhost.length());
                }   
                
                
                

                //If url is thread url then insert it's anchor text and url in DB
                if(filter.isThreadURL(urlwithouthost))
                {
                    
                    System.out.println("\n Got a thread URL > "+urlwithouthost);
                    final String tmpurlwithouthost=urlwithouthost;
                    Platform.runLater(() ->text_area.appendText("\nFound a thread URL : "+tmpurlwithouthost));

                    
                    //Start new thread to obtain the answer then add it in below query
                    
                    String query;
                    
                    
                    if(!allurllist.contains(urlwithhost)&&!allquestionlist.contains(links.get(i).text())){
                        
                        allquestionlist.add(links.get(i).text());
                        allurllist.add(urlwithhost);

                        
                        query="insert into records(id,question,answer_url) values ("+id+",'"+links.get(i).text()+"','"+urlwithhost+"');";
                   
                        if(DB.executeUpdate(query)>=1){
                            System.out.println("Insert query executed , id="+id);
                                 if(id==8){
                                     // Start Question Window Only When First 8 Record Is Obtained
                                          System.out.println("Starting Q_Window , id="+id);
                                          Platform.runLater(new Runnable() {
                                                            public void run() {             
                                                                new Question_Window().start(new Stage());
                                                                 }
                                                            });
                                 }
                                 
                                    
                                 //Adding Content Of Answer
                                 
                                String content="",tmp="";

                                    while(true){
                                    
                                     Document doc1 = Jsoup.connect(urlwithhost).cookie("test","test").userAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_9_2) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/33.0.1750.152 Safari/537.36")
     .followRedirects(true).timeout(300000).get();
                                     
                                     if(!doc1.html().contains("class=\"nxt\">Next</a>")) //When no more flipping URL's are found
                                         break;
                                     content=content+"\nNEXT PAGE CONTENTS\n"+contentExtract(doc1,urlwithhost);   
                                     tmp=doc1.select("a.nxt").first().attr("abs:href");
                                     System.out.println("Next Page URL : "+tmp);
                                     urlwithhost=tmp;
                                     
                                    }
                                    
                                     //SQLi ?
                                     
                                      if(content.contains("'"))
                                         content=content.replaceAll("'", "\"");
                                     
                                    

             
                                     query="update records set answer='"+content+"' where id="+id+";";   
                                     
                                     DB.execute(query);
                                 
                                 System.out.println("Executed");
                                 
                                 id=id+1;
                           }
                        else
                             System.out.println("Failed"); 
                    
                        }
                        
                    }
                    

                
                extracted_urls_path.add(urlwithouthost); // Save extracted links of same host in array
            
            }
            else
            { 
                external_urls.add(links.get(i).absUrl("href")+"");
            } 
        }

        return extracted_urls_path;
    }
    
    
    public String contentExtract(Document doc,String urlwithhost){
            Elements content_body=null;
            String content="";
                    for(String str: divs){
                                         
                        System.out.println("str:"+str+"\nurlwithhost:"+urlwithhost);

                                           if(doc.body().html().contains("<div class=\""+str))
                                                {
                                                    content_body= doc.select("div."+str);
                                                    for(Element e:content_body)
                                                        {
                                                            content=content+e.text()+"\n\n";
                                                         }
                                                    
                                                    break;
                                                }
            
                                            }
                    return content;
    }
    
    public String baseline_url(ArrayList<String> url_paths)
    {
                 //url_paths.get(i).endsWith("/forum")|url_paths.get(i).endsWith("/forum/")|url_paths.get(i).endsWith("/board")|url_paths.get(i).endsWith("/bbs")|url_paths.get(i).endsWith("/community")|url_paths.get(i).endsWith("/discus")|url_paths.get(i).endsWith("/board/")|url_paths.get(i).endsWith("/bbs/")|url_paths.get(i).endsWith("/community/")|url_paths.get(i).endsWith("/discus/"))
        String[] baselinearray={"/forums/index.php","/forums","/forums/","/board","/board/","/bbs","/bbs/","/community","/community/","/discus","/discus/","/board","/board/"};
        
        String baseline_matched_paths="NOT_FOUND";
        for(int i=0;i<url_paths.size();i++)
        {

            String url=url_paths.get(i);
            for(String baseline:baselinearray)
            {
                if(url.endsWith(baseline)){
                    if(url.contains(".")){
                        url=url.substring(0,StringUtils.ordinalIndexOf(url, "/", 2));
                    }
                    baseline_matched_paths=url;
                }
            }
            
            
        }
        return baseline_matched_paths;
    }
    
    
    
}
