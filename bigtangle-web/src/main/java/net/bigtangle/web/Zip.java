package net.bigtangle.web;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 该类实现文件夹压缩成zip文件和zip文件解压
 * 
 * @author Administrator
 *
 */
public class Zip {
	private ZipInputStream zipIn; // 解压Zip
	private ZipOutputStream zipOut; // 压缩Zip
	private ZipEntry zipEntry;
	private static int bufSize; // size of bytes
	private byte[] buf;
	private int readedBytes;

	public Zip() {
		this(512);
	}

	public Zip(int bufSize) {
		this.bufSize = bufSize;
		this.buf = new byte[this.bufSize];
	}

	// 压缩文件夹内的文件
	public void doZip(String zipDirectory) {// zipDirectoryPath:需要压缩的文件夹名
		File file;
		File zipDir;

		zipDir = new File(zipDirectory);
		String zipFileName = zipDirectory + ".zip";// 压缩后生成的zip文件名

		try {
			this.zipOut = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFileName)));
			handleDir(zipDir, this.zipOut);
			this.zipOut.close();
		} catch (Exception ioe) {
			ioe.printStackTrace();
		}
	}

	// 由doZip调用,递归完成目录文件读取
	private void handleDir(File dir, ZipOutputStream zipOut) throws Exception {
		FileInputStream fileIn;
		File[] files;

		files = dir.listFiles();
		if (files.length == 0) {// 如果目录为空,则单独创建之.
			// ZipEntry的isDirectory()方法中,目录以"/"结尾.
			this.zipOut.putNextEntry(new ZipEntry(dir.toString() + "/"));
			this.zipOut.closeEntry();
		} else {// 如果目录不为空,则分别处理目录和文件.
			for (File fileName : files) {

				if (fileName.isDirectory()) {
					handleDir(fileName, this.zipOut);
				} else {
					fileIn = new FileInputStream(fileName);
					String name = dir.getName();
					// 生成的压缩包存放在原目录下
					this.zipOut.putNextEntry(new ZipEntry(name + "/" + fileName.getName().toString()));

					// 此方法存放在该项目目录下
					// this.zipOut.putNextEntry(new ZipEntry(fileName.toString()));
					while ((this.readedBytes = fileIn.read(this.buf)) > 0) {
						this.zipOut.write(this.buf, 0, this.readedBytes);
					}
					this.zipOut.closeEntry();
				}
			}
		}
	}

	// 解压指定zip文件
	public void unZip(String unZipfileName) {// unZipfileName需要解压的zip文件名
		FileOutputStream fileOut;
		File file;
		String f = unZipfileName.substring(0, unZipfileName.length() - 4);
		File ff = new File(f);
		try {
			this.zipIn = new ZipInputStream(new BufferedInputStream(new FileInputStream(unZipfileName)));
			while ((this.zipEntry = this.zipIn.getNextEntry()) != null) {
				file = new File(this.zipEntry.getName());
				if (this.zipEntry.isDirectory()) {
					file.mkdirs();
				} else {
					// 如果指定文件的目录不存在,则创建之.
					File parent = file.getParentFile();
					if (!parent.exists()) {
						parent.mkdirs();
					}
					if (!ff.exists()) {
						ff.mkdir();
					}
					fileOut = new FileOutputStream(f + "/" + file.getName());

					// fileOut = new FileOutputStream(file); 此方法存放到该项目目录下
					while ((this.readedBytes = this.zipIn.read(this.buf)) > 0) {
						fileOut.write(this.buf, 0, this.readedBytes);
					}
					fileOut.close();
				}

				this.zipIn.closeEntry();
			}
		} catch (Exception ioe) {
			ioe.printStackTrace();
		}
	}

	// 设置缓冲区大小
	public void setBufSize(int bufSize) {
		this.bufSize = bufSize;
	}

}
