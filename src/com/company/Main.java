package com.company;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jdk.nashorn.internal.parser.JSONParser;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;


public class Main {
    private static Logger logger = Logger.getLogger(Main.class.getName());

    static String host = "https://www.juso.go.kr";
    static String path = "/info/RoadNameDataList.do?";

    static String params = "type=search&roadCd=&city1=&county1=&town1=&searchType=0&extend=false&keyword=";

    // 도로명 주소는 도로명+건물번호로 이루어져있다.
    // 이 때 도로명은 대로, 로, 길로 끝나며 도로명에는 띄어쓰기나 특수문자가 존재하지 않는다
    // 또한 도로명은 큰 길(ex 대로) 에서 작은도로 갈림길로 간 경우 ㅇㅇ(대)로ㅇㅇ길로 표기하므로 도로명을 체크할 때 "길"을 우선적으로 체크한다
    static List<String> aDoroName = Arrays.asList("길", "로");
    static HashMap<String, Integer> oAddrDict = null;

    public static void main(String[] args) throws Exception {
        Main findAddrAtString = new Main();
        oAddrDict = new HashMap<>();

        Scanner sc = new Scanner(System.in);

        while(true){
            System.out.println("검색하고자 하는 주소의 문자열을 입력해주세요 >>>>> ");
            System.out.println("그만두고 싶다면 Q를 눌러주세요 ");
            String sampleAddr = sc.nextLine();

            if("Q".equals(sampleAddr.toUpperCase(Locale.ROOT)) || "ㅂ".equals(sampleAddr)) break;

            System.out.println(findAddrAtString.findAddr(sampleAddr));
            System.out.println("");

        }
        //String sampleAddr = "한 외 빌 딩";

    }

    /***
     * findAddrAtString string에 도로명이 포함되어있다면 도로명을 return
     * @param sAddr
     * @return String sDoroName
     */
    private String findAddr(String sAddr) throws Exception {

        for(String sDoro : aDoroName){
            List<Integer> aIndexFromAddr = findAllIndex(sAddr, sDoro);

            // index가 한개 이상이면 그 인덱스부터 체크하는 함수 호출
            if(aIndexFromAddr.size() > 0){
                for(int index : aIndexFromAddr){
                    String sCheck = checkStrContainAddr(sAddr.substring(0,index+sDoro.length()) , sDoro);
                    if(!"".equals(sCheck)){
                        return sCheck;
                    }
                }

            }
            // index가 없으면 continue
            else{
             continue;
            }
        }

        return "주소 문자열이 없다";
    }

    /**
     * sTarget에 존재하는 모든 sStr의 위치를 찾아 List로 return
     * @param sTarget   전체 문자열
     * @param sStr      sTarget에서 찾고자 하는 문자
     * @return
     */
    private List<Integer> findAllIndex(String sTarget, String sStr){
        List<Integer> aResult = new ArrayList<>();

        int nIndex = sTarget.indexOf(sStr);

        while(nIndex != -1){
            aResult.add(nIndex);
            nIndex = sTarget.indexOf(sStr, nIndex+sStr.length());
        }
        return aResult;
    }

    private String checkStrContainAddr(String sAddrStr, String sAddr) throws Exception {
        String sResult = "";
        // 고민할 부분
        // 사용자 입력을 받다보니 띄어쓰기의 신뢰도가 떨어짐
        // 띄어쓰기를 제대로 했다고 가정하여 띄어쓰기를 토대로 문자열을 합쳐볼 것인지, 띄어쓰기를 무시하고 합쳐볼것인지 고민필요..
        // api를 쏘다보니 여러번 호출할 수록 성능이 떨어짐

        // 한글과 숫자를 제외한 모든 영문자, 띄어쓰기, 특수문자는 제거한다
        String sTarget = sAddrStr.replaceAll("[^가-힣0-9]","");
        int iSubstrIndex = sAddr.length()+1;

        while(iSubstrIndex <= sTarget.length()){
            if(checkIsAddrStr(sTarget.substring(sTarget.length()-iSubstrIndex, sTarget.length()))){
                sResult = sTarget.substring(sTarget.length()-iSubstrIndex, sTarget.length());
                break;
            }

            iSubstrIndex++;
        }

        return sResult;
    }

    /***
     * 주소검색 api를 사용하여 문자열이 유효한 주소인지 확인
     * @param addrStr
     * @return TRUE : addrStr is address, FALSE : others
     * @throws Exception
     */
    private boolean checkIsAddrStr(String addrStr) throws Exception {
        String urlStr = URLEncoder.encode(addrStr, "UTF-8");
        URL url = new URL(host+path+params+urlStr);
        int cnt = 0; // addrStr을 api를 사용하여 주소검색 하였을때 조회되는 검색어 개수
        String sHtml = "";

        if(oAddrDict.get(addrStr) != null){
            return true;
        }

        HttpsURLConnection connection = null;

        try{
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            //connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //connection.setRequestProperty("Ocp-Apim-Subscription-Key", key);
            connection.setDoOutput(true);

            int responseCode = connection.getResponseCode();

            if(responseCode == HttpsURLConnection.HTTP_OK){
                try (BufferedReader input = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                    String line;
                    StringBuffer buffer = new StringBuffer();
                    while ((line = input.readLine()) != null) {
                        buffer.append(line);
                    }
                    sHtml =  buffer.toString();
                }

                catch(Exception e){
                    logger.warning(getPrintStackTrace(e));
                    throw e;

                }
            }else{
                logger.info("################# Http result not correct ################");
                logger.info("############# responseCode >>> "+responseCode);

            }

        }catch(Exception e){
            logger.warning("################# connection got error ################");
            logger.warning(getPrintStackTrace(e));
            logger.warning("######################################################");

            throw e;
        }

        // api 결과에서 totalcount만 검사한다. totalcount가 0이면 주소가 아닌것
        if(sHtml.substring(sHtml.indexOf("tbody")).indexOf("nodata") == -1){
            oAddrDict.put(addrStr, 1);
            return true;
        }else{
            return false;
        }

    }

    /**
     * exception발생 시 error log trace를 logger를 사용하여 print하기위한 function
     * @param e
     * @return
     */
    public static String getPrintStackTrace(Exception e) {

        StringWriter errors = new StringWriter();
        e.printStackTrace(new PrintWriter(errors));

        return errors.toString();

    }


}