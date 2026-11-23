unit SendData;

interface

uses
  Classes{$IFDEF MSWINDOWS}, Windows{$ENDIF},
  WinSock, SysUtils,ShellAPI;

type
  TSendData = class(TThread)
  private
    ScaleID: Integer;

    procedure SendPLU();
  public
    procedure setScaleID(ScaleID: Integer);
    //constructor create(Scale: TScale; SMFileNo: Integer);
    //destructor Destroy; override;
  protected
    procedure Execute; override;
  end;

implementation

uses
  PLU, Scale, ScaleAPI;

procedure TSendData.setScaleID(ScaleID: Integer);
begin
  Self.ScaleID := ScaleID;
end;

procedure TSendData.Execute;
const
  SEMAPHORE_ALL_ACCESS = $1F0003;
var
  SmHandle: THandle;
  hMutex: THandle; //
begin
  SmHandle := OpenSemaphore(SEMAPHORE_ALL_ACCESS, False, 'SendThreads');
  WaitForSingleObject(SmHandle, INFINITE);
  try
    //线程开始标志
    G_CommMonitor[scaleID].Resume;
    G_ScaleList[scaleID].ScaleThreadStat := 1;
    case G_ScaleList[scaleID].FileNo of
      $25: SendPLU;   //下传 PLU 数据
    end;
  finally
    //线程结束
    G_ScaleList[scaleID].ScaleThreadStat := 0;
    ReleaseSemaphore(SmHandle, 1, nil);  //更新信号量
    if SmHandle<>0 then CloseHandle(SmHandle);
    hMutex :=OpenMutex(MUTEX_ALL_ACCESS,False,PChar('SendThreadMutex'));
    WaitForSingleObject(hMutex,INFINITE);
    Dec(G_ScaleCommThreadCount);   //全局的正在运行的线程计数减1
    ReleaseMutex(hMutex);
    if hMutex<>0 then CloseHandle(hMutex);
  end;
end;


procedure TSendData.SendPLU();
var
  s: TSocket; //
  PLUs: TSMReceiptPLU; //
  Records: ReceiptPLUArray;
  i: Integer;
begin
  s := 0;
  PLUs := TSMReceiptPLU.create;
  try
    //连接到秤
    s := ConnectScale(PChar(G_ScaleList[ScaleID].InterIP), G_ScaleList[ScaleID].PortNo); //创建 socket 连接
    G_ScaleList[ScaleID].CommCount :=1;
    if (s > 0) then //连接成功
    begin
       Records := PLUs.GetDBData;
      if Records <> nil then begin
        for i := Low(Records) to High(Records) do begin
          //得到PLU数据 下传PLu数据
          G_ScaleList[ScaleID].CommResult := ScaleAPI.SendPluData(s, Records[i]); //回收交易明细数据
          case G_ScaleList[ScaleID].CommResult of
            $06:
              begin
                //计数器加1
                Inc(G_ScaleList[ScaleID].CommCount);
                Continue;
              end;
            $E1: Continue;     //写入出错
            $E2: Break;     //没有足够的内存空间
          else
            Break; //未定义的错误
          end;
        end;
      end;
    end;
  finally
    if s > 0 then CloseConnect(s);
    if PLUs <> nil then PLUs.Free;
  end;
end;

end.

