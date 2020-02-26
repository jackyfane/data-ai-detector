package com.yaomalang.aidetector.tools;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DateUtils {


    public static final String PATTERN_SECOND_1 = "yyyy-MM-dd HH:mm:ss";
    public static final String PATTERN_SECOND_2 = "yyyyMMddHHmmss";
    public static final String PATTERN_SECOND_3 = "yyyy/MM/dd HH:mm:ss";

    public static final String PATTERN_MINUTE_1 = "yyyy-MM-dd HH:mm";
    public static final String PATTERN_MINUTE_2 = "yyyyMMddHHmm";
    public static final String PATTERN_MINUTE_3 = "yyyy/MM/dd HH:mm";

    public static final String PATTERN_OURS_1 = "yyyy-MM-dd HH";
    public static final String PATTERN_HOURS_2 = "yyyyMMddHH";
    public static final String PATTERN_HOURS_3 = "yyyy/MM/dd HH";

    public static final String PATTERN_DAY_1 = "yyyy-MM-dd";
    public static final String PATTERN_DAY_2 = "yyyyMMdd";
    public static final String PATTERN_DAY_3 = "yyyy/MM/dd";
    public static final String PATTERN_DAY_4 = "MM/dd/yyyy";

    public static final String PATTERN_MONTH_1 = "yyyy-MM";
    public static final String PATTERN_MONTH_2 = "yyyyMM";
    public static final String PATTERN_MONTH_3 = "yyyy/MM";
    public static final String PATTERN_MONTH_4 = "MM/yyyy";

    public static final String PATTERN_YEAR = "yyyy";

    public static final String PATTERN_DATETIME_VERIFY_1 = "^(\\d{2}|\\d{4})(?:\\-)?([0]{1}\\d{1}|[1]{1}[0-2]{1})(?:\\-)?([0-2]{1}\\d{1}|[3]{1}[0-1]{1})(?:\\s)?([0-1]{1}\\d{1}|[2]{1}[0-3]{1})(?::)?([0-5]{1}\\d{1})(?::)?([0-5]{1}\\d{1})$";
    public static final String PATTERN_DATETIME_VERIFY_2 = "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))$";
    public static final String PATTERN_DATETIME_VERIFY_3 = "^([2-9]\\d{3}((0[1-9]|1[012])(0[1-9]|1\\d|2[0-8])|(0[13456789]|1[012])(29|30)|(0[13578]|1[02])31)|(([2-9]\\d)(0[48]|[2468][048]|[13579][26])|(([2468][048]|[3579][26])00))0229)$";
    public static final String PATTERN_DATETIME_VERIFY_4 = "^(([1-9])|(0[1-9])|(1[0-2]))\\/(([0-9])|([0-2][0-9])|(3[0-1]))\\/(([0-9][0-9])|([1-2][0,9][0-9][0-9]))$";


    /**
     * @param datetime
     * @param pattern
     * @return
     * @throws Exception
     */
    public static long timeToLong(String datetime, String pattern) {

        if ("".equals(datetime) || datetime == null) {
            throw new NullPointerException("the datetime must be not null!");
        }

        if ("".equals(pattern) || pattern == null) {
            pattern = PATTERN_SECOND_1;
        }

        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        Date date = null;
        try {
            date = sdf.parse(datetime);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return date.getTime();
    }


    public static String parserToString(long time, String pattern) {

        SimpleDateFormat sdf = new SimpleDateFormat(pattern);
        String date = sdf.format(new Date(time));
        return date;
    }

    /**
     * 获取当前系统前一天的时间
     *
     * @param format
     * @return
     */
    public static String getLastDate(String format) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(new Date());
        calendar.add(Calendar.DAY_OF_MONTH, -1);
        Date before = calendar.getTime();

        return parserToString(before.getTime(), format);
    }

    /**
     * 获得季节天数
     *
     * @param date
     * @return
     */
    public static int getDayOfSeason(Date date) {
        int day = 0;
        Date[] seasonDates = getSeasonDate(date);
        for (Date date2 : seasonDates) {
            day += getDayOfMonth(date2);
        }
        return day;
    }

    /**
     * 取得月天数
     *
     * @param date
     * @return
     */
    public static int getDayOfMonth(Date date) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        return c.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /**
     * 通过指定开始时间和天数，获取时间
     *
     * @param date
     * @param days
     * @return
     */
    public static Date getDateByAssignStartDateAndDays(Date date, int days) {
        Calendar c = Calendar.getInstance();
        c.setTime(date);
        c.add(Calendar.DAY_OF_YEAR, days);

        return c.getTime();
    }

    /**
     * 取得季度月
     *
     * @param date
     * @return
     */
    public static Date[] getSeasonDate(Date date) {
        Date[] season = new Date[3];

        Calendar c = Calendar.getInstance();
        c.setTime(date);

        int nSeason = getSeason(date);
        if (nSeason == 1) {// 第一季度
            c.set(Calendar.MONTH, Calendar.JANUARY);
            season[0] = c.getTime();
            c.set(Calendar.MONTH, Calendar.FEBRUARY);
            season[1] = c.getTime();
            c.set(Calendar.MONTH, Calendar.MARCH);
            season[2] = c.getTime();
        } else if (nSeason == 2) {// 第二季度
            c.set(Calendar.MONTH, Calendar.APRIL);
            season[0] = c.getTime();
            c.set(Calendar.MONTH, Calendar.MAY);
            season[1] = c.getTime();
            c.set(Calendar.MONTH, Calendar.JUNE);
            season[2] = c.getTime();
        } else if (nSeason == 3) {// 第三季度
            c.set(Calendar.MONTH, Calendar.JULY);
            season[0] = c.getTime();
            c.set(Calendar.MONTH, Calendar.AUGUST);
            season[1] = c.getTime();
            c.set(Calendar.MONTH, Calendar.SEPTEMBER);
            season[2] = c.getTime();
        } else if (nSeason == 4) {// 第四季度
            c.set(Calendar.MONTH, Calendar.OCTOBER);
            season[0] = c.getTime();
            c.set(Calendar.MONTH, Calendar.NOVEMBER);
            season[1] = c.getTime();
            c.set(Calendar.MONTH, Calendar.DECEMBER);
            season[2] = c.getTime();
        }
        return season;
    }

    /**
     * 1 第一季度 2 第二季度 3 第三季度 4 第四季度
     *
     * @param date
     * @return
     */
    public static int getSeason(Date date) {

        int season = 0;

        Calendar c = Calendar.getInstance();
        c.setTime(date);
        int month = c.get(Calendar.MONTH);
        switch (month) {
            case Calendar.JANUARY:
            case Calendar.FEBRUARY:
            case Calendar.MARCH:
                season = 1;
                break;
            case Calendar.APRIL:
            case Calendar.MAY:
            case Calendar.JUNE:
                season = 2;
                break;
            case Calendar.JULY:
            case Calendar.AUGUST:
            case Calendar.SEPTEMBER:
                season = 3;
                break;
            case Calendar.OCTOBER:
            case Calendar.NOVEMBER:
            case Calendar.DECEMBER:
                season = 4;
                break;
            default:
                break;
        }
        return season;
    }

    /**
     * 根据时间返回模式
     *
     * @param datetime
     * @return
     */
    public static String getDateFormat(String datetime) {

        Map<String, String> formatMap = new HashMap<>();
        formatMap.put("yyyy-MM-dd", "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))$");
        formatMap.put("yyyy-MM-dd HH", "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))\\s(([0-1]?[0-9])|([2][0-3]))?$");
        formatMap.put("yyyy-MM-dd HH:mm", "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))\\s(([0-1]?[0-9])|([2][0-3])):([0-5]?[0-9])?$");
        formatMap.put("yyyy-MM-dd HH:mm:ss", "^[0-9]{4}-(((0[13578]|(10|12))-(0[1-9]|[1-2][0-9]|3[0-1]))|(02-(0[1-9]|[1-2][0-9]))|((0[469]|11)-(0[1-9]|[1-2][0-9]|30)))\\s(([0-1]?[0-9])|([2][0-3])):([0-5]?[0-9]):(([0-5]?[0-9]))?$");

        formatMap.put("yyyyMMdd", "^([2-9]\\d{3}((0[1-9]|1[012])(0[1-9]|1\\d|2[0-8])|(0[13456789]|1[012])(29|30)|(0[13578]|1[02])31)|(([2-9]\\d)(0[48]|[2468][048]|[13579][26])|(([2468][048]|[3579][26])00))0229)$");
        formatMap.put("yyyyMMddHH", "^([2-9]\\d{3}((0[1-9]|1[012])(0[1-9]|1\\d|2[0-8])|(0[13456789]|1[012])(29|30)|(0[13578]|1[02])31)|(([2-9]\\d)(0[48]|[2468][048]|[13579][26])|(([2468][048]|[3579][26])00))0229)(([0-1]?[0-9])|([2][0-3]))$");
        formatMap.put("yyyyMMddHHmm", "^([2-9]\\d{3}((0[1-9]|1[012])(0[1-9]|1\\d|2[0-8])|(0[13456789]|1[012])(29|30)|(0[13578]|1[02])31)|(([2-9]\\d)(0[48]|[2468][048]|[13579][26])|(([2468][048]|[3579][26])00))0229)(([0-1]?[0-9])|([2][0-3]))([0-5]?[0-9])$");
        formatMap.put("yyyyMMddHHmmss", "^([2-9]\\d{3}((0[1-9]|1[012])(0[1-9]|1\\d|2[0-8])|(0[13456789]|1[012])(29|30)|(0[13578]|1[02])31)|(([2-9]\\d)(0[48]|[2468][048]|[13579][26])|(([2468][048]|[3579][26])00))0229)(([0-1]?[0-9])|([2][0-3]))([0-5]?[0-9])(([0-5]?[0-9]))$");

        formatMap.put("yyyy/MM/dd", "^\\d{4}\\/\\d{1,2}\\/\\d{1,2}$");
        formatMap.put("yyyy/MM/dd HH", "^\\d{4}\\/\\d{1,2}\\/\\d{1,2}\\s(([0-1]?[0-9])|([2][0-3]))$");
        formatMap.put("yyyy/MM/dd HH:mm", "^\\d{4}\\/\\d{1,2}\\/\\d{1,2}\\s(([0-1]?[0-9])|([2][0-3])):([0-5]?[0-9])$");
        formatMap.put("yyyy/MM/dd HH:mm:ss", "^\\d{4}\\/\\d{1,2}\\/\\d{1,2}\\s(([0-1]?[0-9])|([2][0-3])):([0-5]?[0-9]):([0-5]?[0-9])$");

        formatMap.put("yyyy", "^[0-9]{4}$");
        formatMap.put("yyyyMM", "^\\d{4}\\d{2}$");
        formatMap.put("yyyy/MM", "^\\d{4}\\/\\d{1,2}$");

        String format = "";
        for (String key : formatMap.keySet()) {
            String regular = formatMap.get(key);
            if (Pattern.compile(regular).matcher(datetime).matches()) {
                format = key;
                break;
            }
        }

//        Pattern p = Pattern.compile(PATTERN_DATETIME_VERIFY_1);
//        Matcher m = p.matcher(datetime);
//        if (m.find()) {
//            if (datetime.contains("-"))
//                return PATTERN_SECOND_1;
//            else
//                return PATTERN_SECOND_2;
//        }
//
//        p = Pattern.compile(PATTERN_DATETIME_VERIFY_2);
//        m = p.matcher(datetime);
//        if (m.find()) {
//            return PATTERN_DAY_1;
//        }
//
//        p = Pattern.compile(PATTERN_DATETIME_VERIFY_3);
//        m = p.matcher(datetime);
//        if (m.find()) {
//            return PATTERN_DAY_2;
//        }
//
//        p = Pattern.compile(PATTERN_DATETIME_VERIFY_4);
//        m = p.matcher(datetime);
//        if (m.find()) {
//            return PATTERN_DAY_4;
//        }

        return format;
    }

    /**
     * @param dateStr
     * @return
     */
    public static String formatDate(String dateStr) {

        HashMap<String, String> dateRegFormat = new HashMap<String, String>();
        dateRegFormat.put("^\\d{4}\\D+\\d{1,2}\\D+\\d{1,2}\\D+\\d{1,2}\\D+\\d{1,2}\\D+\\d{1,2}\\D*$",
                "yyyy-MM-dd-HH-mm-ss");//2014年3月12日 13时5分34秒，2014-03-12 12:05:34，2014/3/12 12:5:34
        dateRegFormat.put("^\\d{4}\\D+\\d{2}\\D+\\d{2}\\D+\\d{2}\\D+\\d{2}$",
                "yyyy-MM-dd-HH-mm");//2014-03-12 12:05
        dateRegFormat.put("^\\d{4}\\D+\\d{2}\\D+\\d{2}\\D+\\d{2}$",
                "yyyy-MM-dd-HH");//2014-03-12 12
        dateRegFormat.put("^\\d{4}\\D+\\d{2}\\D+\\d{2}$", "yyyy-MM-dd");//2014-03-12
        dateRegFormat.put("^\\d{4}\\D+\\d{2}$", "yyyy-MM");//2014-03
        dateRegFormat.put("^\\d{4}$", "yyyy");//2014
        dateRegFormat.put("^\\d{14}$", "yyyyMMddHHmmss");//20140312120534
        dateRegFormat.put("^\\d{12}$", "yyyyMMddHHmm");//201403121205
        dateRegFormat.put("^\\d{10}$", "yyyyMMddHH");//2014031212
        dateRegFormat.put("^\\d{8}$", "yyyyMMdd");//20140312
        dateRegFormat.put("^\\d{6}$", "yyyyMM");//201403

        String curDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
        DateFormat formatter1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        DateFormat formatter2;
        String dateReplace;
        String strSuccess = "";
        try {
            for (String key : dateRegFormat.keySet()) {
                if (Pattern.compile(key).matcher(dateStr).matches()) {
                    formatter2 = new SimpleDateFormat(dateRegFormat.get(key));
                    if (key.equals("^\\d{2}\\s*:\\s*\\d{2}\\s*:\\s*\\d{2}$")
                            || key.equals("^\\d{2}\\s*:\\s*\\d{2}$")) {//13:05:34 或 13:05 拼接当前日期
                        dateStr = curDate + "-" + dateStr;
                    } else if (key.equals("^\\d{1,2}\\D+\\d{1,2}$")) {//21.1 (日.月) 拼接当前年份
                        dateStr = curDate.substring(0, 4) + "-" + dateStr;
                    }
                    dateReplace = dateStr.replaceAll("\\D+", "-");
                    // System.out.println(dateRegExpArr[i]);
                    strSuccess = formatter1.format(formatter2.parse(dateReplace));
                    break;
                }
            }
        } catch (Exception e) {
            System.err.println("-----------------日期格式无效:" + dateStr);
            throw new Exception("日期格式无效");
        } finally {
            return strSuccess;
        }
    }

    public static void main(String[] args) {

        String[] dateStrArray = new String[]{
                "2014-03-12 12:05:34",
                "2014-03-12 12:05",
                "2014-03-12 12",
                "2014-03-12",
                "2014-03",
                "2014",
                "20140312120534",
                "2014/03/12 12:05:34",
                "2014/3/12 12:5:34",
                "2014年3月12日 13时5分34秒",
                "201403121205",
                "1234567890",
                "20140312",
                "201403",
                "2000 13 33 13 13 13",
                "30.12.2013",
                "12.21.2013",
                "21.1",
                "13:05:34",
                "12:05",
                "14.1.8",
                "14.10.18"
        };
        for (int i = 0; i < dateStrArray.length; i++) {
            System.out.println(dateStrArray[i] + "------------------------------".substring(1, 30 - dateStrArray[i].length()) + formatDate(dateStrArray[i]));
        }

    }
}
