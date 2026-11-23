// vcDemoDlg.h : header file
//

#if !defined(AFX_VCDEMODLG_H__BCE0D284_193F_4B2E_ADC6_DEEA79ADBD1E__INCLUDED_)
#define AFX_VCDEMODLG_H__BCE0D284_193F_4B2E_ADC6_DEEA79ADBD1E__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

/////////////////////////////////////////////////////////////////////////////
// CVcDemoDlg dialog

class CVcDemoDlg : public CDialog
{
// Construction
public:
	CVcDemoDlg(CWnd* pParent = NULL);	// standard constructor

// Dialog Data
	//{{AFX_DATA(CVcDemoDlg)
	enum { IDD = IDD_VCDEMO_DIALOG };
		// NOTE: the ClassWizard will add data members here
	//}}AFX_DATA

	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CVcDemoDlg)
	protected:
	virtual void DoDataExchange(CDataExchange* pDX);	// DDX/DDV support
	//}}AFX_VIRTUAL

// Implementation
protected:
	HICON m_hIcon;

	// Generated message map functions
	//{{AFX_MSG(CVcDemoDlg)
	virtual BOOL OnInitDialog();
	afx_msg void OnSysCommand(UINT nID, LPARAM lParam);
	afx_msg void OnPaint();
	afx_msg HCURSOR OnQueryDragIcon();
	afx_msg void OnButtonDown();
	afx_msg void OnButtonReceive();
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_VCDEMODLG_H__BCE0D284_193F_4B2E_ADC6_DEEA79ADBD1E__INCLUDED_)
