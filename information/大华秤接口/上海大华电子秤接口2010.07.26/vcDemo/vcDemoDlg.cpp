// vcDemoDlg.cpp : implementation file
//

#include "stdafx.h"
#include "vcDemo.h"
#include "vcDemoDlg.h"

#ifdef _DEBUG
#define new DEBUG_NEW
#undef THIS_FILE
static char THIS_FILE[] = __FILE__;
#endif

/////////////////////////////////////////////////////////////////////////////
// CAboutDlg dialog used for App About

class CAboutDlg : public CDialog
{
public:
	CAboutDlg();

// Dialog Data
	//{{AFX_DATA(CAboutDlg)
	enum { IDD = IDD_ABOUTBOX };
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CAboutDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);    // DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	//{{AFX_MSG(CAboutDlg)
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

CAboutDlg::CAboutDlg() : CDialog(CAboutDlg::IDD)
{
	//{{AFX_DATA_INIT(CAboutDlg)
	//}}AFX_DATA_INIT
}

void CAboutDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CAboutDlg)
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CAboutDlg, CDialog)
	//{{AFX_MSG_MAP(CAboutDlg)
		// No message handlers
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CVcDemoDlg dialog

CVcDemoDlg::CVcDemoDlg(CWnd* pParent /*=NULL*/)
	: CDialog(CVcDemoDlg::IDD, pParent)
{
	//{{AFX_DATA_INIT(CVcDemoDlg)
		// NOTE: the ClassWizard will add member initialization here
	//}}AFX_DATA_INIT
	// Note that LoadIcon does not require a subsequent DestroyIcon in Win32
	m_hIcon = AfxGetApp()->LoadIcon(IDR_MAINFRAME);
}

void CVcDemoDlg::DoDataExchange(CDataExchange* pDX)
{
	CDialog::DoDataExchange(pDX);
	//{{AFX_DATA_MAP(CVcDemoDlg)
		// NOTE: the ClassWizard will add DDX and DDV calls here
	//}}AFX_DATA_MAP
}

BEGIN_MESSAGE_MAP(CVcDemoDlg, CDialog)
	//{{AFX_MSG_MAP(CVcDemoDlg)
	ON_WM_SYSCOMMAND()
	ON_WM_PAINT()
	ON_WM_QUERYDRAGICON()
	ON_BN_CLICKED(IDC_BUTTON_DOWN, OnButtonDown)
	ON_BN_CLICKED(IDC_BUTTON_RECEIVE, OnButtonReceive)
	//}}AFX_MSG_MAP
END_MESSAGE_MAP()

/////////////////////////////////////////////////////////////////////////////
// CVcDemoDlg message handlers

BOOL CVcDemoDlg::OnInitDialog()
{
	CDialog::OnInitDialog();

	// Add "About..." menu item to system menu.

	// IDM_ABOUTBOX must be in the system command range.
	ASSERT((IDM_ABOUTBOX & 0xFFF0) == IDM_ABOUTBOX);
	ASSERT(IDM_ABOUTBOX < 0xF000);

	CMenu* pSysMenu = GetSystemMenu(FALSE);
	if (pSysMenu != NULL)
	{
		CString strAboutMenu;
		strAboutMenu.LoadString(IDS_ABOUTBOX);
		if (!strAboutMenu.IsEmpty())
		{
			pSysMenu->AppendMenu(MF_SEPARATOR);
			pSysMenu->AppendMenu(MF_STRING, IDM_ABOUTBOX, strAboutMenu);
		}
	}

	// Set the icon for this dialog.  The framework does this automatically
	//  when the application's main window is not a dialog
	SetIcon(m_hIcon, TRUE);			// Set big icon
	SetIcon(m_hIcon, FALSE);		// Set small icon
	
	// TODO: Add extra initialization here
	
	return TRUE;  // return TRUE  unless you set the focus to a control
}

void CVcDemoDlg::OnSysCommand(UINT nID, LPARAM lParam)
{
	if ((nID & 0xFFF0) == IDM_ABOUTBOX)
	{
		CAboutDlg dlgAbout;
		dlgAbout.DoModal();
	}
	else
	{
		CDialog::OnSysCommand(nID, lParam);
	}
}

// If you add a minimize button to your dialog, you will need the code below
//  to draw the icon.  For MFC applications using the document/view model,
//  this is automatically done for you by the framework.

void CVcDemoDlg::OnPaint() 
{
	if (IsIconic())
	{
		CPaintDC dc(this); // device context for painting

		SendMessage(WM_ICONERASEBKGND, (WPARAM) dc.GetSafeHdc(), 0);

		// Center icon in client rectangle
		int cxIcon = GetSystemMetrics(SM_CXICON);
		int cyIcon = GetSystemMetrics(SM_CYICON);
		CRect rect;
		GetClientRect(&rect);
		int x = (rect.Width() - cxIcon + 1) / 2;
		int y = (rect.Height() - cyIcon + 1) / 2;

		// Draw the icon
		dc.DrawIcon(x, y, m_hIcon);
	}
	else
	{
		CDialog::OnPaint();
	}
}

// The system calls this to obtain the cursor to display while the user drags
//  the minimized window.
HCURSOR CVcDemoDlg::OnQueryDragIcon()
{
	return (HCURSOR) m_hIcon;
}
#define MY_IMPORT __declspec(dllimport)

MY_IMPORT	int WINAPI	dhSendPluDefault();
MY_IMPORT	int WINAPI	dhRcvRdDefault();

//	0			成功
//	-1			连接失败
//	-2			通讯失败(服务器无响应，或服务器返回数据错误)
//	-3			发送失败(写发送缓冲失败)
//	-4			连接断开
//	-5			待发送数据为空
//	-6			用户手动退出
//	-7			目标IP为空
//	-9			通讯未全部成功，某IP出错，具体信息保存在return.txt中 .
//	-12			dhpluversion.int为空或设置错误
//	-13			dhplupathname.ini为空或设置错误
//	-14			dhplupathname.ini中读取的Plu路径不对,所指向的Plu文件不存在
//	-15			Plu文本为空或者设置错误(如:Plu版本与Plu文本内容不一致等)
void CVcDemoDlg::OnButtonDown() 
{
	// TODO: Add your control notification handler code here
	GetDlgItem(IDC_BUTTON_DOWN)->EnableWindow(FALSE);
	CString	strMsg[]={"成功","连接失败","通讯失败","发送失败","连接断开",
		"待发送数据为空","用户手动退出","目标IP为空","通讯未全部成功，某IP出错，具体信息保存在return.txt中 .",
		"","",
		"dhpluversion.int为空或设置错误",
		"dhplupathname.ini为空或设置错误",
		"dhplupathname.ini中读取的Plu路径不对,所指向的Plu文件不存在",
		"Plu文本为空或者设置错误(如:Plu版本与Plu文本内容不一致等)",
		"","","","","",
	};
	int iRet=dhSendPluDefault();
	
	MessageBox(strMsg[iRet*(-1)]);
	GetDlgItem(IDC_BUTTON_DOWN)->EnableWindow(true);
}

void CVcDemoDlg::OnButtonReceive() 
{
	// TODO: Add your control notification handler code here
	GetDlgItem(IDC_BUTTON_DOWN)->EnableWindow(FALSE);
	CString	strMsg[]={"成功","连接失败","通讯失败","发送失败","连接断开",
		"待发送数据为空","用户手动退出","目标IP为空","通讯未全部成功，某IP出错，具体信息保存在return.txt中 .",
		"","",
		"dhpluversion.int为空或设置错误",
		"dhplupathname.ini为空或设置错误",
		"dhplupathname.ini中读取的Plu路径不对,所指向的Plu文件不存在",
		"Plu文本为空或者设置错误(如:Plu版本与Plu文本内容不一致等)",
		"","","","","",
	};
	int iRet=dhRcvRdDefault();
	
	MessageBox(strMsg[iRet*(-1)]);
	GetDlgItem(IDC_BUTTON_DOWN)->EnableWindow(true);
}
