package com.company;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import jdk.nashorn.internal.parser.JSONParser;

import java.io.*;
import java.net.*;
import java.util.Arrays;
import java.util.Locale;
import java.util.Scanner;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.net.ssl.HttpsURLConnection;

/****
 * kakao rest api key 152626fd24ec59c0689721b0d8bf0f8b
 * 주소 api key devU01TX0FVVEgyMDIyMDkxNTE0NDUwMjExMjk3NDE=
 */
public class Main {
    private static Logger logger = Logger.getLogger(Main.class.getName());

    static String host = "https://business.juso.go.kr";
    static String path = "/addrlink/addrLinkApi.do";

    static String key = "devU01TX0FVVEgyMDIyMDkxNTE0NDUwMjExMjk3NDE=";

    static String params = "?confmKey="+key+"&currentPage=1&countPerPage=10&resultType=json&keyword=";

    // 일반적인 주소형식을 정의 ex. 종로, 백석로, 구로3동 ...etc
    static String checkStrArr = "빌딩;건물;로;길;읍;면;동;가";
    static String wordsBreak = "에;로;으로";
    static String regExp = "([\\D\\d]*([가-힣a-zA-Z0-9]){2,}(로|길)[\\D\\d]*)|([\\D\\d]*([가-힣a-zA-Z0-9]){2,}(읍|면|동|가)[\\D\\d]*)";
    static String regExp2 = "(([가-힣a-zA-Z0-9]){2,}(로|길))|(([가-힣a-zA-Z0-9]){2,}(읍|동))";

    public static void main(String[] args) throws Exception {
        Main findAddrAtString = new Main();

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

    private String findAddr(String addrStr) throws Exception {
        String[] splitStr = null;

        Pattern pattern = Pattern.compile(regExp);
        Matcher matcher = pattern.matcher(addrStr);

        // 1. 도로명, 법정동 정규식을 한번에 패스하고 띄어쓰기가 훌륭하게 된 케이스
        if(Pattern.matches(regExp, addrStr)){
            splitStr = addrStr.split(" ");
            for(String str : splitStr){
                if(str.endsWith("로") || str.endsWith("길") || str.endsWith("읍") || str.endsWith("면") || str.endsWith("동") || str.endsWith("가")){
                    // api호출
                    if(checkIsAddrStr(str))
                        return str;
                }
            }
        }

        // 로or길 등이 포함은 되어있는지 체크 ex) 백 석 로 26 or 도화-2길 or 한외빌딩
        String[] checkArr = checkStrArr.split(";");
        String checkResult= "";

        for(String chkStr : checkArr){
            if(addrStr.contains(chkStr)){
                checkResult = checkIsAddrStrWithChkStr(addrStr, chkStr, " ");
                if(!"".equals(checkResult)) return checkResult;
            }
        }

        // 띄어쓰기를 아예안한 케이스 ex) 마포구백석로26길에있는분식집
        for(String chkStr : checkArr){
            checkResult = checkIsAddrStrWithChkStr(addrStr.replaceAll(" ",""), chkStr);
            if(!"".equals(checkResult)) return checkResult;
        }

        // 그 어떤 케이스도 통과하지 못한 케이스는 띄어쓰기를 모두 없앤 뒤 장소를 말하는 어절로 쪼갠 뒤 다시 체크한다 (ex. 에)
        String[] checkWithWordsArr = wordsBreak.split(";");
        for(String chkStr : checkWithWordsArr){
            if(addrStr.contains(chkStr)){
                checkResult = checkIsAddrStrWithChkStr(addrStr, chkStr);
                if(!"".equals(checkResult)) return checkResult;
            }

        }

        return "문자열 내에 주소없음";
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

        HttpsURLConnection connection = null;

        try{
            connection = (HttpsURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            //connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            //connection.setRequestProperty("Ocp-Apim-Subscription-Key", key);
            connection.setDoOutput(true);

            int responseCode = connection.getResponseCode();

            if(responseCode == HttpsURLConnection.HTTP_OK){
                try(BufferedReader in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()))){

                    String line = "";
                    String result = "";
                    while ((line = in.readLine()) != null) {
                        result += line;
                    }
                    JsonParser jsonParser = new JsonParser();
                    JsonObject jsonobj = null;
                    try{
                        jsonobj = (JsonObject) jsonParser.parse(result);
                    }catch(JsonParseException e){
                        logger.warning(getPrintStackTrace(e));
                        throw e;
                    }
                    //{"results":{"common":{"errorMessage":"정상","countPerPage":"10","totalCount":"0","errorCode":"0","currentPage":"1"},"juso":[]}}

                    cnt = jsonobj.get("results").getAsJsonObject().get("common").getAsJsonObject().get("totalCount").getAsInt();


                }catch(Exception e){
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
        return (cnt > 0)? true : false;

    }

    /***
     * check String을 사용하여 문자열을 쪼갠 뒤 주소문자를 찾는다
     * @param str 원 문자열
     * @param chkStr check String (ex, 로, 길, 동, 읍...)
     * @return 주소로 인식된 문자열
     * @throws Exception
     */
    private String checkIsAddrStrWithChkStr(String str, String chkStr) throws Exception {
        return checkIsAddrStrWithChkStr(str, chkStr, "");
    }

    /***
     * check String을 사용하여 문자열을 쪼갠 뒤 주소문자를 찾는다
     * @param str 원 문자열
     * @param chkStr check String (ex, 로, 길, 동, 읍...)
     * @param splitStr 쪼개고자 하는 단위
     * @return 주소로 인식된 문자열
     * @throws Exception
     */
    private String checkIsAddrStrWithChkStr(String str, String chkStr, String splitStr) throws Exception {
        String[] strArr = str.split(chkStr);
        String checkAddr = "";
        String result = "";

        // chkStr로 쪼갠 배열의 마지막 index는 chkStr이 포함되어있지않으므로 loop체크에서 제외한다
        for(int index = 0; index <= strArr.length-1 ; index++){
            // 배열에 담긴 문자열을 공백으로 분리한 뒤 뒤에서부터 하나씩 합쳐가며 주소인지 확인
            String[] words = strArr[index].split(splitStr);

            for(int index2 = words.length-1 ; index2 >= 0 ; index2--){
                checkAddr= words[index2].replaceAll("[^가-힣a-zA-Z0-9]","") + checkAddr;
                // 주소를 찾았다면 return한다
                if(checkIsAddrStr(checkAddr+chkStr)){
                    result = checkAddr+chkStr;
                }
                else{
                    if("".equals(result)) checkAddr = "";
                    else return result;
                }

            }

            if(!"".equals(result)) return result;
        }

        return result;
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