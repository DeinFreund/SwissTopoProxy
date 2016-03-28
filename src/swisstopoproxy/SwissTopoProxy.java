/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package swisstopoproxy;

import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.net.Socket;
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
        ServerSocket serverSocket = new ServerSocket(80);

        // repeatedly wait for connections, and process
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
                                if (line.contains("GET /")) {
                                    int start = line.indexOf("21781/") + ("21781/").length();
                                    int end = line.indexOf(".jpeg");
                                    String nums[] = line.substring(start, end).split("/");
                                    rqZ = Integer.valueOf(nums[0]);
                                    rqY = Integer.valueOf(nums[1]);
                                    rqX = Integer.valueOf(nums[2]);
                                    rqLat1 = tile2lat(rqY, rqZ);
                                    rqLat2 = tile2lat(rqY + 1, rqZ);
                                    rqLon1 = tile2lon(rqX, rqZ);
                                    rqLon2 = tile2lon(rqX + 1, rqZ);
                                    
                                    System.out.println("Serving tile " + rqX + "|" + rqY + ", Zoom: " + rqZ );
                                }
                            }
                            
                            System.out.println("WGS84: " + rqLon1 + "° E " + rqLat1 + "° N\n");

                            File file = new File("R:\\tiles\\served\\" + rqZ + "_" + rqY + "_" + rqX + ".jpeg");
                            BufferedImage finalImg;
                            if (!file.exists()) {
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
                            out.write("Content-Type: image/jpeg" + "\r\n");
                            out.write("Content-Length: " + byteArr.length + "\r\n");
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
