/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package swisstopoproxy;

import java.awt.Color;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/**
 *
 * @author User
 */
public class SwissTopoProxy {

    final static int MAX_BUF_SIZE = 1000;

    static Map<Point, BufferedImage> loadedImages = new LinkedHashMap(MAX_BUF_SIZE + 1, .75F, true) {

        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return size() > MAX_BUF_SIZE;
        }
    };

    static int getRGB(int tileX, int tileY) throws IOException {
            Point point = new Point(tileX / 256, tileY / 256);
            if (!loadedImages.containsKey(point)) {
                File file = new File("R:\\tiles\\22\\" + point.y + "_" + point.x + ".jpg");
                if (!file.exists()) return 0;
                loadedImages.put(point, ImageIO.read(file));
            }

            BufferedImage img = loadedImages.get(point);
            return img.getRGB(tileX % 256, tileY % 256);
    }

    static BufferedImage resampleImage(double x1, double y1, double x2, double y2, int w, int h) throws IOException{

        y1 = ((3600 * y1 - 169028.66) / 10000);
        x1 = ((3600 * x1 - 26782.5) / 10000);
        y2 = ((3600 * y2 - 169028.66) / 10000);
        x2 = ((3600 * x2 - 26782.5) / 10000);
        BufferedImage res = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int cnt = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                for (double subX = 0; subX < 0.999; subX += 0.26) {
                    for (double subY = 0; subY < 0.999; subY += 0.26) {
                        double tileX = (x + subX) / w * x2 + (w - x - subX) / w * x1;
                        double tileY = (y + subY) / h * y2 + (h - y - subY) / h * y1;

                        double rtileY = 256 * (350000 - ((200147.07 + (308807.95 * tileY) + (3745.25 * tileX * tileX) + (76.63 * tileY * tileY)) - (194.56 * tileX * tileX * tileY)) + (119.79 * tileY * tileY * tileY)) / 640d;
                        double rtileX = 256 *((600072.37 + (211455.93 * tileX)) - (10938.51 * tileX * tileY)- (0.36 * tileX * tileY * tileY)- (44.54 * tileX * tileX * tileX) - 420000) / 640d;
                        
                        int col = getRGB((int)rtileX, (int)rtileY);
                        r += (col & 0xFF0000) >> 16;
                        g += (col & 0x00FF00) >> 8;
                        b += (col & 0x0000FF);
                        cnt ++;
                    }
                }
                res.setRGB(x, y, ((r / cnt) << 16) | ((g / cnt) << 8) | (b / cnt));
            }
        }
        return res;
    }
    
    static double tile2lon(int x, int z) {
        double n = Math.pow(2, z);
        return x / n * 360 - 180;

    }

    static double tile2lat(int y, int z) {
        double n = Math.pow(2, z);
        double lat_rad = Math.atan(Math.sinh(Math.PI * (1 - 2 * y / n)));
        return lat_rad * 180 / Math.PI;
    }

    public static void main(String[] args) throws Exception {
        loadedImages = Collections.synchronizedMap(loadedImages);
        int port = 80;
        ServerSocket serverSocket = new ServerSocket(port);
        //System.out.println("Server listening on port: " + port);

        // repeatedly wait for connections, and process
        double __lat = 46.500;
        double __lon = 7.43831;
        System.out.println(LV95.wgs2chHP(__lat, __lon).x + " " + LV95.wgs2chHP(__lat, __lon).y);
        for (int i = 0; i < 8; i++) {
            new Thread() {
                public void run() {
                    while (true) {
                        try {
                            Socket clientSocket = serverSocket.accept();

                            BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                            BufferedWriter out = new BufferedWriter(new OutputStreamWriter(clientSocket.getOutputStream()));

                            String s;
                            List<String> lines = new ArrayList();
                            while ((s = in.readLine()) != null) {
                                lines.add(s);
                                if (s.isEmpty()) {
                                    break;
                                }
                            }
                            
                            int rqX = 0, rqY = 0, rqZ = 0;
                            double rqLat1 = 0, rqLat2 = 0;
                            double rqLon1 = 0, rqLon2 = 0;
                            for (String line : lines) {
                                if (line.contains("Host: ")) {
                                    line = "Host: wmts8.geo.admin.ch";
                                }
                                if (line.contains("GET /")) {
                                    int start = line.indexOf("21781/") + ("21781/").length();
                                    int end = line.indexOf(".jpeg");
                                    //System.out.println(start + ":" + end + ":" + line);
                                    String nums[] = line.substring(start, end).split("/");
                                    int z = Integer.valueOf(nums[0]);
                                    int y = Integer.valueOf(nums[1]);
                                    int x = Integer.valueOf(nums[2]);
                                    int ox = x;
                                    int oy = y;
                                    int fac = 1 << (z - 15);
                                    int mask = 0x0FFFFFFF - 2047;
                                    rqLat1 = tile2lat(y, z);
                                    rqLat2 = tile2lat(y + 1, z);
                                    rqLon1 = tile2lon(x, z);
                                    rqLon2 = tile2lon(x + 1, z);

                                    rqX = x;
                                    rqY = y;
                                    rqZ = z;
                                    System.out.println("fac: " + fac);
                                    line = "GET /1.0.0/ch.swisstopo.pixelkarte-farbe/default/20151231/21781/" + z + "/" + y + "/" + x + ".jpeg HTTP/1.1";
                                    System.out.println(ox + "|" + oy + " @" + z + " -> " + x + "|" + y);
                                }
                            }
                            
                            double rqLvX1 = ApproxSwissProj.WGS84toLV03(rqLat1, rqLon1, 0)[0] + 2e6;
                            double rqLvY1 = ApproxSwissProj.WGS84toLV03(rqLat1, rqLon1, 0)[1] + 1e6;
                            double rqLvX2 = ApproxSwissProj.WGS84toLV03(rqLat2, rqLon2, 0)[0] + 2e6;
                            double rqLvY2 = ApproxSwissProj.WGS84toLV03(rqLat2, rqLon2, 0)[1] + 1e6;
                            System.out.println("Requested " + rqLat1 + ", " + rqLon1);
                            System.out.println("Requested " + rqLvX1 + ", " + rqLvY1);

                            File file = new File("R:\\tiles\\served\\" + rqZ + "_" + rqY + "_" + rqX + ".jpeg");
                            BufferedImage finalImg;
                            if (true || !file.exists()) {
                                finalImg = resampleImage(rqLon1, rqLat1, rqLon2, rqLat2, 256, 256);
                                ImageIO.write(finalImg, "jpg", file);
                            } else {
                                finalImg = ImageIO.read(file);
                            }
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            ImageIO.write(finalImg, "jpg", baos);
                            baos.flush();
                            byte[] byteArr = baos.toByteArray();
                            baos.close();

                            out.write("HTTP/1.1 200 OK" + "\r\n");
                            out.write("Accept-Ranges: bytes" + "\r\n");
                            out.write("Accept-Ranges: bytes" + "\r\n");
                            out.write("Access-Control-Allow-Origin: *" + "\r\n");
                            out.write("Age: 0" + "\r\n");
                            out.write("Cache-Control: public, max-age=1800" + "\r\n");
                            out.write("Content-Type: image/jpeg" + "\r\n");
                            out.write("Date: Sun, 27 Mar 2016 21:54:02 GMT" + "\r\n");
                            out.write("ETag: \"fd6cb926e6ecfdaebf1e8f5e2b5892d7\"" + "\r\n");
                            out.write("Last-Modified: Wed, 12 Aug 2015 13:40:36 GMT" + "\r\n");
                            out.write("Server: AmazonS3" + "\r\n");
                            out.write("Via: 1.1 varnish-v4" + "\r\n");
                            out.write("X-Cache: MISS" + "\r\n");
                            out.write("X-Varnish: 115812449" + "\r\n");
                            out.write("Content-Length: " + byteArr.length + "\r\n");
                            out.write("Connection: keep-alive" + "\r\n");
                            out.write("\r\n");
                            out.flush();

                            clientSocket.getOutputStream().write(byteArr);
                            clientSocket.getOutputStream().flush();

                            out.close();
                            in.close();
                            clientSocket.close();
                        } catch (Exception ex) {
                            ex.printStackTrace();
                        }
                    }
                }

            }.start();
        }
    }
}
