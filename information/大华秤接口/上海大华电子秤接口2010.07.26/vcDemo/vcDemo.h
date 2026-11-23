// vcDemo.h : main header file for the VCDEMO application
//

#if !defined(AFX_VCDEMO_H__B873BBA9_7214_4B29_A8A4_5D0294D6D99C__INCLUDED_)
#define AFX_VCDEMO_H__B873BBA9_7214_4B29_A8A4_5D0294D6D99C__INCLUDED_

#if _MSC_VER > 1000
#pragma once
#endif // _MSC_VER > 1000

#ifndef __AFXWIN_H__
	#error include 'stdafx.h' before including this file for PCH
#endif

#include "resource.h"		// main symbols

/////////////////////////////////////////////////////////////////////////////
// CVcDemoApp:
// See vcDemo.cpp for the implementation of this class
//

class CVcDemoApp : public CWinApp
{
public:
	CVcDemoApp();

// Overrides
	// ClassWizard generated virtual function overrides
	//{{AFX_VIRTUAL(CVcDemoApp)
	public:
	virtual BOOL InitInstance();
	//}}AFX_VIRTUAL

// Implementation

	//{{AFX_MSG(CVcDemoApp)
		// NOTE - the ClassWizard will add and remove member functions here.
		//    DO NOT EDIT what you see in these blocks of generated code !
	//}}AFX_MSG
	DECLARE_MESSAGE_MAP()
};


/////////////////////////////////////////////////////////////////////////////

//{{AFX_INSERT_LOCATION}}
// Microsoft Visual C++ will insert additional declarations immediately before the previous line.

#endif // !defined(AFX_VCDEMO_H__B873BBA9_7214_4B29_A8A4_5D0294D6D99C__INCLUDED_)
