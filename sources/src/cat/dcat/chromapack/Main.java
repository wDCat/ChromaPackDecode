/**
 * Created By DCat 2017/01/11
 */
package cat.dcat.chromapack;


import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.color.ColorSpace;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Stack;

public class Main {
    static class float2 {
        float v1;
        float v2;

        public float2(float v1, float v2) {
            this.v1 = v1;
            this.v2 = v2;
        }

        float2 mul(float2 obj) {
            return new float2(v1 * obj.v1, v2 * obj.v2);
        }

        float2 add(float2 obj) {
            return new float2(v1 + obj.v1, v2 + obj.v2);
        }
    }

    static class double3 {
        double v1;
        double v2;
        double v3;

        public double3(double v1, double v2, double v3) {
            this.v1 = v1;
            this.v2 = v2;
            this.v3 = v3;
        }

        @Override
        public String toString() {
            return "double3{" +
                    "v1=" + v1 +
                    ", v2=" + v2 +
                    ", v3=" + v3 +
                    '}';
        }

        public int toColor(float alpha) {
            if (v1 > 1.0f) v1 = 1.0f;
            if (v2 > 1.0f) v2 = 1.0f;
            if (v3 > 1.0f) v3 = 1.0f;
            if (v1 < 0.0f) v1 = 0.0f;
            if (v2 < 0.0f) v2 = 0.0f;
            if (v3 < 0.0f) v3 = 0.0f;
            return new Color((float) v1, (float) v2, (float) v3, alpha).getRGB();
        }
    }


    private static float RGB_Y(Color rgb) {
        return 0.299f * rgb.getRed() + 0.587f * rgb.getGreen() + 0.114f * rgb.getBlue();
    }

    private static float RGB_Cb(Color rgb) {
        return -0.168736f * rgb.getRed() - 0.331264f * rgb.getGreen() + 0.5f * rgb.getBlue();
    }

    private static float RGB_Cr(Color rgb) {
        return 0.5f * rgb.getRed() - 0.418688f * rgb.getGreen() - 0.081312f * rgb.getBlue();
    }

    private static float RGB_Ya(Color rgb) {
        if (rgb.getAlpha() < 0.5f)
            return 0;
        else
            return RGB_Y(rgb) * 255 / 256 + 1.0f / 256;
    }

    static double3 YCbCrtoRGB(double y, double cb, double cr) {
        return new double3(
                (y + 1.402 * cr),
                (y - 0.344136 * cb - 0.714136 * cr),
                (y + 1.772 * cb)
        );
    }

    private static float getAlpha(int rgba, boolean isGray) {
        if (isGray) return getAlphaByGray(rgba);
        return (rgba >> 24 & 0xFF) / 255f;
    }

    static float getAlphaByGray(int rgba) {
        return (rgba & 0xFF) / 255f;
    }

    private static BufferedImage decodeBackground(BufferedImage image, boolean isGray, boolean removeBolder) {
        int tw = image.getWidth();
        int th = image.getHeight();
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_4BYTE_ABGR);
        System.out.println("size(x,y):(" + tw + "   " + th);

        float2 timg = new float2(tw, th);
        for (int x = 0; x < tw; x++) {
            for (int y = 0; y < th; y++) {
                try {
                    /*---------------------------
                    * |          |              |
                    * |   cb     |              |
                    * |----------|       y      |
                    * |          |              |
                    * |   cr     |              |
                    * ---------------------------
                    * */
                    float2 pos = new float2(x, y);
                    float2 y_pos = pos.mul(new float2(2.0f / 3.0f, 1.0f)).add(timg.mul(new float2(1.0f / 3.0f, 0.0f)));
                    float2 cb_pos = pos.mul(new float2(1.0f / 3.0f, 0.5f));
                    float2 cr_pos = pos.mul(new float2(1.0f / 3.0f, 0.5f)).add(timg.mul(new float2(0f, 0.5f)));
                    double y_ = getAlpha(image.getRGB(new Float(y_pos.v1).intValue(), new Float(y_pos.v2).intValue()), isGray);

                    double cb_ = getAlpha(image.getRGB(new Float(cb_pos.v1).intValue(), new Float(cb_pos.v2).intValue()), isGray) - 0.5f;
                    double cr_ = getAlpha(image.getRGB(new Float(cr_pos.v1).intValue(), new Float(cr_pos.v2).intValue()), isGray) - 0.5f;
                    double3 color = YCbCrtoRGB(y_, cb_, cr_);
                    float alpha_ = 1f;
                    if (removeBolder) {
                        if ((x < tw * 0.15 || x > tw * 0.85) || (y < th * 0.15 || y > th * 0.85)) {
                            if (y_ == 0.0f) alpha_ = 0.0f;
                            else if (y_ < 0.02f) alpha_ = 0.01f;
                        }
                    }
                    out.setRGB(x, y, color.toColor(alpha_));
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    break;
                }
            }
        }
        out = zoomImage(out, new Float(image.getWidth() * (2f / 3f)).intValue(), image.getHeight());
        return out;
    }

    private static BufferedImage decodeUnitImage(BufferedImage image, boolean isGray) {
        int tw = image.getWidth();
        int th = image.getHeight();
        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_4BYTE_ABGR);
        System.out.println("size(x,y):(" + tw + "   " + th);

        float2 timg = new float2(tw, th);
        for (int x = 0; x < tw; x++) {
            for (int y = 0; y < th; y++) {
                out.setRGB(x, y, 0x00FFFFFF);
                try {
                    /*----------------------------------
                    * |          |              |      |
                    * |   cb     |              |      |
                    * |----------|       y      |alpha |
                    * |          |              |      |
                    * |   cr     |              |      |
                    * ----------------------------------
                    * */
                    float2 pos = new float2(x, y);
                    float2 y_pos = pos.mul(new float2(2.0f / 4.0f, 1.0f)).add(timg.mul(new float2(1.0f / 4.0f, 0.0f)));
                    float2 cb_pos = pos.mul(new float2(1.0f / 4.0f, 0.5f));
                    float2 cr_pos = pos.mul(new float2(1.0f / 4.0f, 0.5f)).add(timg.mul(new float2(0f, 0.5f)));
                    float2 alpha_pos = pos.mul(new float2(1.0f / 4.0f, 1.0f)).add(timg.mul(new float2(3.0f / 4.0f, 0.0f)));
                    double y_ = getAlpha(image.getRGB(new Float(y_pos.v1).intValue(), new Float(y_pos.v2).intValue()), isGray);
                    double cb_ = getAlpha(image.getRGB(new Float(cb_pos.v1).intValue(), new Float(cb_pos.v2).intValue()), isGray) - 0.5f;
                    double cr_ = getAlpha(image.getRGB(new Float(cr_pos.v1).intValue(), new Float(cr_pos.v2).intValue()), isGray) - 0.5f;
                    double alpha_ = getAlpha(image.getRGB(new Float(alpha_pos.v1).intValue(), new Float(alpha_pos.v2).intValue()), isGray);
                    double3 color = YCbCrtoRGB(y_, cb_, cr_);
                    out.setRGB(x, y, color.toColor((float) alpha_));
                    /*
                    if (y_ - 1.0f / 256f < 0) {
                        double3 color = YCbCrtoRGB(y_, cb_, cr_);
                        out.setRGB(x, y, color.toColor(alpha_));
                        continue;
                    }
                    y_ = (y_ - 1.0f / 256f) * 256.0f / 255f;
                    if(alpha_==0){
                        //TODO
                    }else {
                        double3 color = YCbCrtoRGB(y_, cb_, cr_);
                        out.setRGB(x, y, color.toColor(alpha_));
                    }*/
                } catch (Exception e) {
                    e.printStackTrace(System.err);
                    break;
                }
            }
        }
        out = zoomImage(out, new Float(image.getWidth() * (2f / 4f)).intValue(), image.getHeight());
        return out;
    }

    private static void usage() {
        System.err.println("Usage:");
        System.err.println("//TODO:");
    }

    public static void main(String[] args) throws IOException {
        PrintStream out = System.out;
        out.println("-----------------------------------------");
        out.println("            Just a Hello World ");
        out.println("-----------------------------------------");
        out.println("By DCat 2017/01/12");
        out.println("2017/12/09 Updated");
        Stack<String> sources = new Stack<>();
        boolean hasAlpha = false;
        boolean isGray = false;
        boolean removeBolder = false;
        for (int x = 0; x < args.length; x++) {
            switch (args[x]) {
                case "-g":
                    isGray = true;
                    break;
                case "-a":
                    hasAlpha = true;
                    break;
                case "-r":
                    removeBolder = true;
                    break;
                default:
                    sources.push(args[x]);
            }
        }
        if (sources.size() == 0) {
            usage();
            System.exit(1);
        }
        out.println("-----------------------------------------");
        out.println("[*]HasAlpha:" + hasAlpha + "    isGray:" + isGray);
        for (String source : sources) {
            out.println("[*]Decoding " + source);
            BufferedImage image = ImageIO.read(new File(source));
            BufferedImage outImage;
            if (hasAlpha)
                outImage = decodeUnitImage(image, isGray);
            else
                outImage = decodeBackground(image, isGray, removeBolder);
            String outFile = source + "_decoded.png";
            out.println("[*]Done.");
            ImageIO.write(outImage, "PNG", new File(outFile));
            out.println("[*]PNG Saved to " + outFile);
        }
        out.println("-----------------------------------------");
    }

    public static BufferedImage zoomImage(BufferedImage originalImage, int width, int height) {
        BufferedImage newImage = new BufferedImage(width, height, originalImage.getType());
        Graphics g = newImage.getGraphics();
        g.drawImage(originalImage, 0, 0, width, height, null);
        g.dispose();
        return newImage;
    }

    static Color setAlpha(Color orig, float alpha) {
        return new Color(orig.getRed(), orig.getBlue(), orig.getGreen(), alpha);
    }
            /*
        Color[] source = new Color[tw*th*2];
        Color[] pixels = new Color[tw*th*2];
        int mask=0;
        for (int x = 0; x < tw; x++) {
            for (int y = 0; y < th; y++) {
                source[mask]=new Color(out.getRGB(x,y));
                pixels[mask++]=new Color(out.getRGB(x,y));
            }
        }

        //texture.Resize(tw * 3 / 2, th, TextureFormat.Alpha8, false);


        int i1 = 0;
        int i2 = 0;


        for (int iy = 0; iy < th; iy++)
        {
            for (int ix = 0; ix < tw; ix++)
            {
                try {
                    pixels[i2++] = setAlpha(pixels[i2++], RGB_Y(source[i1++]));
                }catch (Exception e){
                    System.err.println("i2:"+i2+"  i1:"+i1);
                    e.printStackTrace(System.err);
                }
            }

            i2 += tw / 2;
        }

        i1 = 0;
        i2 = tw;
        int i3 = (tw * 3 / 2) * th / 2 + tw;

        for (int iy = 0; iy < th / 2; iy++)
        {
            for (int ix = 0; ix < tw / 2; ix++)
            {
                int ws = (source[i1].getRGB() + source[i1 + 1].getRGB() + source[i1 + tw].getRGB() + source[i1 + tw + 1].getRGB()) / 4;
                pixels[i2++]=setAlpha( pixels[i2++],RGB_Cr(new Color(ws)) + 0.5f);
                pixels[i3++]=setAlpha( pixels[i3++],RGB_Cr(new Color(ws)) + 0.5f);
                i1 += 2;
            }
            i1 += tw;
            i2 += tw;
            i3 += tw;
        }
        mask=0;
        for (int x = 0; x < tw; x++) {
            for (int y = 0; y < th; y++) {
                image.setRGB(x,y,pixels[mask++].getRGB());
            }
        }*/
}
