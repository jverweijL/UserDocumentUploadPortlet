package userdocumentupload.portlet;

import com.liferay.document.library.kernel.model.DLFileEntry;
import com.liferay.document.library.kernel.model.DLFolder;
import com.liferay.document.library.kernel.model.DLFolderConstants;
import com.liferay.document.library.kernel.service.DLAppServiceUtil;
import com.liferay.portal.kernel.exception.PortalException;
import com.liferay.portal.kernel.exception.SystemException;
import com.liferay.portal.kernel.repository.model.FileEntry;
import com.liferay.portal.kernel.repository.model.Folder;
import com.liferay.portal.kernel.service.ServiceContext;
import com.liferay.portal.kernel.service.ServiceContextFactory;
import com.liferay.portal.kernel.theme.ThemeDisplay;
import com.liferay.portal.kernel.upload.UploadPortletRequest;
import com.liferay.portal.kernel.util.PortalUtil;
import com.liferay.portal.kernel.util.WebKeys;
import userdocumentupload.constants.UserDocumentUploadPortletKeys;
import com.liferay.portal.kernel.portlet.bridges.mvc.MVCPortlet;
import javax.portlet.ActionRequest;
import javax.portlet.ActionResponse;
import javax.portlet.Portlet;
import javax.portlet.PortletException;
import org.osgi.service.component.annotations.Component;
//import com.liferay.util.portlet.PortletProps;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author jverweij
 */
@Component(
	immediate = true,
	property = {
		"com.liferay.portlet.display-category=category.sample",
		"com.liferay.portlet.header-portlet-css=/css/main.css",
		"com.liferay.portlet.instanceable=true",
		"javax.portlet.display-name=UserDocumentUpload",
		"javax.portlet.init-param.template-path=/",
		"javax.portlet.init-param.view-template=/view.jsp",
		"javax.portlet.name=" + UserDocumentUploadPortletKeys.USERDOCUMENTUPLOAD,
		"javax.portlet.resource-bundle=content.Language",
		"javax.portlet.security-role-ref=power-user,user"
	},
	service = Portlet.class
)
/**
 * Portlet to upload documents into the D&M Library
 * Code is based on https://liferayiseasy.blogspot.com/2015/07/how-to-upload-documents-and-files-in.html
 */
public class UserDocumentUploadPortlet extends MVCPortlet {
	private static String ROOT_FOLDER_NAME = "upload_folder";//PortletProps.get("fileupload.folder.name");
	private static String ROOT_FOLDER_DESCRIPTION = "defined by upload portlet";//PortletProps.get("fileupload.folder.description");
	private static long PARENT_FOLDER_ID = DLFolderConstants.DEFAULT_PARENT_FOLDER_ID;

	public void uploadDocument(ActionRequest actionRequest, ActionResponse actionResponse) throws IOException, PortletException, PortalException, SystemException
	{
		ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);
		createFolder(actionRequest, themeDisplay);
		fileUpload(themeDisplay, actionRequest);
	}

	public void downloadFiles(ActionRequest actionRequest,ActionResponse actionResponse) throws IOException,PortletException, PortalException, SystemException
	{
		ThemeDisplay themeDisplay = (ThemeDisplay) actionRequest.getAttribute(WebKeys.THEME_DISPLAY);

		Map<String,String> urlMap = getAllFileLink(themeDisplay);
		actionRequest.setAttribute("urlMap", urlMap);
		actionResponse.setRenderParameter("jspPage","/html/documentupload/download.jsp");
	}
	public Folder createFolder(ActionRequest actionRequest, ThemeDisplay themeDisplay)
	{
		boolean folderExist = isFolderExist(themeDisplay);
		Folder folder = null;
		if (!folderExist) {
			long repositoryId = themeDisplay.getScopeGroupId();
			try {
				ServiceContext serviceContext = ServiceContextFactory.getInstance(DLFolder.class.getName(), actionRequest);
				if (!isRootFolderExist(themeDisplay)) {
					folder = DLAppServiceUtil.addFolder(repositoryId, PARENT_FOLDER_ID, ROOT_FOLDER_NAME, ROOT_FOLDER_DESCRIPTION, serviceContext);
				} else {
					folder = DLAppServiceUtil.getFolder(themeDisplay.getScopeGroupId(), PARENT_FOLDER_ID, ROOT_FOLDER_NAME);
				}
				System.out.println("Main folder: " + folder.getName() + " / " + folder.getFolderId());
				folder = DLAppServiceUtil.addFolder(repositoryId,folder.getFolderId(),Long.toString(themeDisplay.getUserId()),"user folder", serviceContext);
			} catch (PortalException e1) {
				e1.printStackTrace();
			} catch (SystemException e1) {
				e1.printStackTrace();
			}
		}
		return folder;
	}

	public boolean isRootFolderExist(ThemeDisplay themeDisplay){
		boolean folderExist = false;
		try {
			DLAppServiceUtil.getFolder(themeDisplay.getScopeGroupId(), PARENT_FOLDER_ID, ROOT_FOLDER_NAME);
			folderExist = true;
			System.out.println("Folder is already Exist");
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return folderExist;
	}


	public boolean isFolderExist(ThemeDisplay themeDisplay){
		boolean folderExist = false;
		try {
			Folder folder = DLAppServiceUtil.getFolder(themeDisplay.getScopeGroupId(), PARENT_FOLDER_ID, ROOT_FOLDER_NAME);
			DLAppServiceUtil.getFolder(themeDisplay.getScopeGroupId(), folder.getFolderId(), Long.toString(themeDisplay.getUserId()));
			folderExist = true;
			System.out.println("Folder is already Exist");
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return folderExist;
	}

	public Folder getFolder(ThemeDisplay themeDisplay){
		Folder folder = null;
		try {
			folder = DLAppServiceUtil.getFolder(themeDisplay.getScopeGroupId(), PARENT_FOLDER_ID, ROOT_FOLDER_NAME);
			folder = DLAppServiceUtil.getFolder(themeDisplay.getScopeGroupId(), folder.getFolderId(), Long.toString(themeDisplay.getUserId()));
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
		return folder;
	}


	public void fileUpload(ThemeDisplay themeDisplay,ActionRequest actionRequest)
	{
		UploadPortletRequest uploadPortletRequest = PortalUtil.getUploadPortletRequest(actionRequest);

		String fileName=uploadPortletRequest.getFileName("uploadedFile");
		File file = uploadPortletRequest.getFile("uploadedFile");
		System.out.println(file.length() / (1024 * 1024) + " mb");

		String mimeType = uploadPortletRequest.getContentType("uploadedFile");
		String title = fileName;
		String description = "This file is added via programatically";
		long repositoryId = themeDisplay.getScopeGroupId();
		System.out.println("Title=>"+title);
		try
		{
			Folder folder = getFolder(themeDisplay);
			ServiceContext serviceContext = ServiceContextFactory.getInstance(DLFileEntry.class.getName(), actionRequest);
			InputStream is = new FileInputStream( file );
			DLAppServiceUtil.addFileEntry(repositoryId, folder.getFolderId(), fileName, mimeType,
					title, description, "", is, file.length(), serviceContext);

		} catch (Exception e)
		{
			System.out.println(e.getMessage());
			e.printStackTrace();
		}

	}

	public Map<String, String> getAllFileLink(ThemeDisplay themeDisplay){
		Map<String, String> urlMap = new HashMap<String, String>();//key = file name ,value = url
		long repositoryId = themeDisplay.getScopeGroupId();
		try {
			Folder folder =getFolder(themeDisplay);
			List<FileEntry> fileEntries = DLAppServiceUtil.getFileEntries(repositoryId, folder.getFolderId());
			for (FileEntry file : fileEntries) {
				String url = themeDisplay.getPortalURL() + themeDisplay.getPathContext() + "/documents/" + themeDisplay.getScopeGroupId() + "/" +
						file.getFolderId() +  "/" +file.getTitle() ;
				urlMap.put(file.getTitle().split("\\.")[0], url);// remove jpg or pdf

			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return urlMap;

	}
}